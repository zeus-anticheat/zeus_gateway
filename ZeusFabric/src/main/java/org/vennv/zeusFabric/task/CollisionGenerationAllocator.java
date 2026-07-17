package org.vennv.zeusFabric.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;
import java.util.function.LongSupplier;

final class CollisionGenerationAllocator {
    static final String HIGH_WATER_PROPERTY = "org.vennv.zeusFabric.collisionGenerationHighWater";

    interface Store {
        long load();
        void save(long value);
    }

    private final LongSupplier clock;
    private final Store store;

    CollisionGenerationAllocator(LongSupplier clock, Store store) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.store = Objects.requireNonNull(store, "store");
    }

    static CollisionGenerationAllocator persistent() {
        return new CollisionGenerationAllocator(
                System::currentTimeMillis,
                new FileStore(Path.of("config", "zeusfabric-collision-generation.high-water")));
    }

    long next() {
        Properties properties = System.getProperties();
        synchronized (properties) {
            long current = Math.max(
                    Math.max(read(properties.getProperty(HIGH_WATER_PROPERTY)), store.load()),
                    Math.max(1L, clock.getAsLong()));
            long next = Math.incrementExact(current);
            store.save(next);
            properties.setProperty(HIGH_WATER_PROPERTY, Long.toString(next));
            return next;
        }
    }

    private static long read(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw new IllegalStateException("collision generation high-water is negative");
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new IllegalStateException("collision generation high-water is invalid", failure);
        }
    }

    private static final class FileStore implements Store {
        private final Path path;

        private FileStore(Path path) {
            this.path = path;
        }

        @Override
        public long load() {
            if (!Files.exists(path)) {
                return 0L;
            }
            try {
                return read(Files.readString(path, StandardCharsets.UTF_8).trim());
            } catch (IOException failure) {
                throw new IllegalStateException("failed to read collision generation high-water", failure);
            }
        }

        @Override
        public void save(long value) {
            Path parent = path.getParent();
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try {
                Files.createDirectories(parent);
                Files.writeString(temporary, Long.toString(value), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException failure) {
                throw new IllegalStateException("failed to persist collision generation high-water", failure);
            }
        }
    }
}
