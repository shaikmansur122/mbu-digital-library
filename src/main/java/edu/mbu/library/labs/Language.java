package edu.mbu.library.labs;

/** Languages supported by lab experiments and the code editor. */
public enum Language {
    PYTHON("Python", false),
    JAVA("Java", true),
    CPP("C++", true),
    C("C", true),
    SQL("SQL", false);

    private final String label;
    private final boolean runsOnServer;

    Language(String label, boolean runsOnServer) {
        this.label = label;
        this.runsOnServer = runsOnServer;
    }

    public String getLabel() { return label; }

    /** Python and SQL run inside the student's browser; Java, C and C++ are compiled and run by the server. */
    public boolean isRunsOnServer() { return runsOnServer; }
}
