package net.cloudclient.core;

/** Minimal logging seam so the core never depends on a logging framework. */
public interface Log {

    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);

    /** Writes to stdout/stderr; used by tests and by headless tooling. */
    Log CONSOLE = new Log() {
        @Override
        public void info(String message) {
            System.out.println("[Cloud] " + message);
        }

        @Override
        public void warn(String message) {
            System.out.println("[Cloud][warn] " + message);
        }

        @Override
        public void error(String message, Throwable cause) {
            System.err.println("[Cloud][error] " + message);
            if (cause != null) cause.printStackTrace(System.err);
        }
    };
}
