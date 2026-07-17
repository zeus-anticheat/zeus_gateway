package org.vennv.zeusFabric.contract;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.discovery.ModResolver;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.fabricmc.loader.impl.util.version.VersionParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class FabricLoaderSelectionTest {
    public static void main(String[] args) throws Exception {
        require(args.length >= 2, "usage: <outer-jar> <target>...");
        Path outerPath = Path.of(args[0]);
        require(Files.isRegularFile(outerPath), "outer JAR missing");
        for (int index = 1; index < args.length; index++) {
            verifyTarget(outerPath, args[index]);
        }
    }

    private static void verifyTarget(Path outerPath, String target) throws Exception {
        VersionOverrides versions = new VersionOverrides();
        Path overrideDirectory = Files.createTempDirectory("zeusfabric-loader-selection");
        try {
            DependencyOverrides dependencies = new DependencyOverrides(overrideDirectory);
            List<ModCandidateImpl> nested = new ArrayList<>();
            LoaderModMetadata outerMetadata;
            try (JarFile outer = new JarFile(outerPath.toFile())) {
                outerMetadata = metadata(outer.getInputStream(requireEntry(outer, "fabric.mod.json")), outerPath.toString(), versions, dependencies);
                for (Object value : outerMetadata.getJars()) {
                    String nestedPath = ((net.fabricmc.loader.impl.metadata.NestedJarEntry) value).getFile();
                    try (InputStream input = outer.getInputStream(requireEntry(outer, nestedPath))) {
                        nested.add(createNested(nestedPath, metadataFromJar(input, nestedPath, versions, dependencies)));
                    }
                }
            }

            ModCandidateImpl parent = createPlain(outerPath, outerMetadata, nested);
            Method addParent = ModCandidateImpl.class.getDeclaredMethod("addParent", ModCandidateImpl.class);
            addParent.setAccessible(true);
            for (ModCandidateImpl child : nested) {
                require((boolean) addParent.invoke(child, parent), "nested parent link failed");
            }

            List<ModCandidateImpl> candidates = new ArrayList<>();
            candidates.add(parent);
            candidates.addAll(nested);
            candidates.add(root("minecraft", target, versions, dependencies));
            candidates.add(root("fabricloader", "0.18.1", versions, dependencies));
            candidates.add(root("java", "21", versions, dependencies));
            candidates.add(root("fabric-api", "1", versions, dependencies));

            List<ModCandidateImpl> selected = ModResolver.resolve(candidates, EnvType.SERVER, new HashMap<String, Set<ModCandidateImpl>>());
            List<ModCandidateImpl> adapters = selected.stream()
                    .filter(candidate -> candidate.getId().equals("zeusfabric"))
                    .toList();
            require(adapters.size() == 1, "Fabric Loader did not select exactly one adapter for " + target);
            ModDependency minecraft = adapters.getFirst().getDependencies().stream()
                    .filter(dependency -> dependency.getModId().equals("minecraft"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("selected adapter lacks Minecraft constraint"));
            require(minecraft.matches(VersionParser.parse(target, false)), "selected adapter does not match " + target);
            require(adapters.getFirst().getLocalPath().contains("ZeusFabric-" + target + "-"),
                    "Fabric Loader selected wrong adapter for " + target + ": " + adapters.getFirst().getLocalPath());
        } finally {
            Files.deleteIfExists(overrideDirectory);
        }
    }

    private static ModCandidateImpl root(
            String id,
            String version,
            VersionOverrides versions,
            DependencyOverrides dependencies) throws Exception {
        String json = "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\"}";
        return createPlain(Path.of(id), metadata(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), id, versions, dependencies), Collections.emptyList());
    }

    private static LoaderModMetadata metadataFromJar(
            InputStream jarInput,
            String path,
            VersionOverrides versions,
            DependencyOverrides dependencies) throws Exception {
        Path temporary = Files.createTempFile("zeusfabric-nested", ".jar");
        try {
            Files.copy(jarInput, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (JarFile jar = new JarFile(temporary.toFile())) {
                return metadata(jar.getInputStream(requireEntry(jar, "fabric.mod.json")), path, versions, dependencies);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static LoaderModMetadata metadata(
            InputStream input,
            String path,
            VersionOverrides versions,
            DependencyOverrides dependencies) throws Exception {
        return ModMetadataParser.parseMetadata(input, path, Collections.emptyList(), versions, dependencies, false);
    }

    @SuppressWarnings("unchecked")
    private static ModCandidateImpl createPlain(
            Path path,
            LoaderModMetadata metadata,
            Collection<ModCandidateImpl> nested) throws Exception {
        Method method = ModCandidateImpl.class.getDeclaredMethod(
                "createPlain", List.class, LoaderModMetadata.class, boolean.class, Collection.class);
        method.setAccessible(true);
        return (ModCandidateImpl) method.invoke(null, List.of(path), metadata, false, nested);
    }

    private static ModCandidateImpl createNested(String path, LoaderModMetadata metadata) throws Exception {
        Method method = ModCandidateImpl.class.getDeclaredMethod(
                "createNested", String.class, long.class, LoaderModMetadata.class, boolean.class, Collection.class);
        method.setAccessible(true);
        return (ModCandidateImpl) method.invoke(null, path, 0L, metadata, false, Collections.emptyList());
    }

    private static JarEntry requireEntry(JarFile jar, String path) {
        JarEntry entry = jar.getJarEntry(path);
        if (entry == null) {
            throw new AssertionError("missing JAR entry " + path);
        }
        return entry;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
