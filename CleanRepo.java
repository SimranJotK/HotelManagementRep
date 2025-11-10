import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;

public class CleanRepo {

    private static final long SIZE_THRESHOLD = 50 * 1024 * 1024; // 50MB in bytes
    private static final List<String> PATTERNS_TO_REMOVE = Arrays.asList(
        "node_modules", "venv", ".env", "__pycache__", ".DS_Store", "*.log", "*.tmp", "dist", "build", "target"
    );
    private static final String GITIGNORE_PATH = ".gitignore";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Starting repository cleanup...");
        System.out.println("Size threshold: " + (SIZE_THRESHOLD / (1024 * 1024)) + " MB");

        List<Path> largeFiles = new ArrayList<>();
        List<Path> toRemove = new ArrayList<>();

        try {
            Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    long size = attrs.size();
                    if (size > SIZE_THRESHOLD) {
                        largeFiles.add(file);
                    }
                    if (matchesPattern(file)) {
                        toRemove.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (matchesPattern(dir)) {
                        toRemove.add(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            return;
        }

        System.out.println("\nLarge files (>50MB):");
        for (Path file : largeFiles) {
            System.out.println(file + " - " + (Files.size(file) / (1024 * 1024)) + " MB");
        }

        System.out.println("\nFiles/folders to remove:");
        for (Path item : toRemove) {
            System.out.println(item);
        }

        System.out.print("\nConfirm deletion? (yes/no): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!confirm.equals("yes")) {
            System.out.println("Cleanup cancelled.");
            return;
        }

        List<Path> deleted = new ArrayList<>();
        for (Path item : toRemove) {
            try {
                if (Files.isDirectory(item)) {
                    deleteDirectory(item);
                } else {
                    Files.delete(item);
                }
                deleted.add(item);
            } catch (IOException e) {
                System.err.println("Failed to delete " + item + ": " + e.getMessage());
            }
        }

        // Update .gitignore
        updateGitignore();

        System.out.println("\nSummary:");
        System.out.println("Deleted items: " + deleted.size());
        for (Path item : deleted) {
            System.out.println("- " + item);
        }
        System.out.println("Remaining large files: " + largeFiles.size());
    }

    private static boolean matchesPattern(Path path) {
        String name = path.getFileName().toString();
        for (String pattern : PATTERNS_TO_REMOVE) {
            if (pattern.startsWith("*")) {
                Pattern p = Pattern.compile(pattern.replace("*", ".*"));
                if (p.matcher(name).matches()) {
                    return true;
                }
            } else if (name.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void updateGitignore() {
        try {
            Path gitignore = Paths.get(GITIGNORE_PATH);
            List<String> lines = Files.readAllLines(gitignore);
            Set<String> existing = new HashSet<>(lines);

            boolean updated = false;
            for (String pattern : PATTERNS_TO_REMOVE) {
                if (!existing.contains(pattern)) {
                    lines.add(pattern);
                    updated = true;
                }
            }

            if (updated) {
                Files.write(gitignore, lines);
                System.out.println(".gitignore updated.");
            } else {
                System.out.println(".gitignore already up to date.");
            }
        } catch (IOException e) {
            System.err.println("Failed to update .gitignore: " + e.getMessage());
        }
    }
}
