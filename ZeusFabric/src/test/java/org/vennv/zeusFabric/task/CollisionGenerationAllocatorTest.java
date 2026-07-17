package org.vennv.zeusFabric.task;

public final class CollisionGenerationAllocatorTest {
    public static void main(String[] args) {
        String previous = System.getProperty(CollisionGenerationAllocator.HIGH_WATER_PROPERTY);
        try {
            System.clearProperty(CollisionGenerationAllocator.HIGH_WATER_PROPERTY);
            MemoryStore store = new MemoryStore();
            CollisionGenerationAllocator first = new CollisionGenerationAllocator(() -> 100L, store);
            require(first.next() == 101L, "epoch seed was not incremented");
            CollisionGenerationAllocator lowerClockReload = new CollisionGenerationAllocator(() -> 50L, store);
            require(lowerClockReload.next() == 102L, "lower reload clock reused generation");
            CollisionGenerationAllocator equalClockReload = new CollisionGenerationAllocator(() -> 102L, store);
            require(equalClockReload.next() == 103L, "equal reload clock reused generation");
            store.value = 1L;
            CollisionGenerationAllocator sharedProcessReload = new CollisionGenerationAllocator(() -> 1L, store);
            require(sharedProcessReload.next() == 104L, "process high-water was not shared across reload instances");
            System.setProperty(CollisionGenerationAllocator.HIGH_WATER_PROPERTY, "200");
            store.value = 300L;
            CollisionGenerationAllocator persistedReload = new CollisionGenerationAllocator(() -> 250L, store);
            require(persistedReload.next() == 301L, "persisted high-water did not win");
        } finally {
            if (previous == null) {
                System.clearProperty(CollisionGenerationAllocator.HIGH_WATER_PROPERTY);
            } else {
                System.setProperty(CollisionGenerationAllocator.HIGH_WATER_PROPERTY, previous);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MemoryStore implements CollisionGenerationAllocator.Store {
        private long value;

        @Override
        public long load() {
            return value;
        }

        @Override
        public void save(long value) {
            this.value = value;
        }
    }
}
