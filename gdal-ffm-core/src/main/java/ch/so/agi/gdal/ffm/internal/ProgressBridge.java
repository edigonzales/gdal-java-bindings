package ch.so.agi.gdal.ffm.internal;

import ch.so.agi.gdal.ffm.ProgressCallback;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

final class ProgressBridge {
    private static final AtomicLong IDS = new AtomicLong(1L);
    private static final ConcurrentMap<Long, CallbackState> CALLBACKS = new ConcurrentHashMap<>();
    private static final MethodHandle TRAMPOLINE;
    private static final FunctionDescriptor PROGRESS_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_DOUBLE,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
    );

    static {
        try {
            TRAMPOLINE = MethodHandles.lookup()
                    .findStatic(
                            ProgressBridge.class,
                            "invoke",
                            MethodType.methodType(int.class, double.class, MemorySegment.class, MemorySegment.class)
                    );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ProgressBridge() {
    }

    static ProgressHandle create(ProgressCallback callback, Arena arena) {
        if (callback == null) {
            return ProgressHandle.NONE;
        }

        long id = IDS.getAndIncrement();
        CallbackState state = new CallbackState(callback);
        CALLBACKS.put(id, state);

        MemorySegment userData = arena.allocate(ValueLayout.JAVA_LONG);
        userData.set(ValueLayout.JAVA_LONG, 0, id);

        MemorySegment stub = Linker.nativeLinker().upcallStub(TRAMPOLINE, PROGRESS_DESCRIPTOR, arena);
        return new ProgressHandle(id, stub, userData, state);
    }

    @SuppressWarnings("unused")
    private static int invoke(double complete, MemorySegment message, MemorySegment userData) {
        if (CStrings.isNull(userData)) {
            return 1;
        }

        long id = userData.reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG, 0);
        CallbackState state = CALLBACKS.get(id);
        if (state == null) {
            return 1;
        }

        try {
            boolean keepGoing = state.callback.onProgress(complete, CStrings.fromCString(message));
            return keepGoing ? 1 : 0;
        } catch (RuntimeException ex) {
            state.failure = ex;
            return 0;
        }
    }

    static final class ProgressHandle implements AutoCloseable {
        static final ProgressHandle NONE = new ProgressHandle(0L, MemorySegment.NULL, MemorySegment.NULL, null);

        private final long id;
        private final MemorySegment callbackFn;
        private final MemorySegment userData;
        private final CallbackState state;

        private ProgressHandle(long id, MemorySegment callbackFn, MemorySegment userData, CallbackState state) {
            this.id = id;
            this.callbackFn = callbackFn;
            this.userData = userData;
            this.state = state;
        }

        MemorySegment callbackFn() {
            return callbackFn;
        }

        MemorySegment userData() {
            return userData;
        }

        RuntimeException callbackFailure() {
            return state == null ? null : state.failure;
        }

        @Override
        public void close() {
            if (id != 0L) {
                CALLBACKS.remove(id);
            }
        }
    }

    private static final class CallbackState {
        private final ProgressCallback callback;
        private volatile RuntimeException failure;

        private CallbackState(ProgressCallback callback) {
            this.callback = callback;
        }
    }
}
