import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * NestView - a small desktop file explorer for learning Java Swing.
 * All file operations use java.nio.file.Files and Path.
 */
public class NestView extends JFrame {
    private Path currentFolder;
    private final JTextField locationField = new JTextField();
    private final JTextField searchField = new JTextField(16);
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"Name", "Type", "Size", "Modified"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable fileTable = new JTable(tableModel);
    private boolean foldersFirst = true;
    private List<Path> shownPaths = new ArrayList<>();

    public NestView() {
        super("NestView");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(createWindowIcon());
        setSize(900, 580);
        setMinimumSize(new Dimension(680, 420));
        setLocationRelativeTo(null);
        currentFolder = Path.of(System.getProperty("user.home"));
        buildWindow();
        loadFolder(currentFolder);
    }

    private void buildWindow() {
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(new Color(245, 247, 250));
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));
        JButton upButton = new JButton("↑ Up");
        upButton.addActionListener(e -> goUp());
        locationField.addActionListener(e -> navigateToLocation());
        top.add(upButton, BorderLayout.WEST);
        top.add(locationField, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createTitledBorder("Quick access"));
        side.setPreferredSize(new Dimension(165, 0));
        addQuickLink(side, "Home", Path.of(System.getProperty("user.home")));
        addQuickLink(side, "Desktop", null);
        addQuickLink(side, "Documents", null);
        addQuickLink(side, "Downloads", Path.of(System.getProperty("user.home"), "Downloads"));
        FileSystems.getDefault().getRootDirectories().forEach(root ->
                addQuickLink(side, root.toString(), root));
        add(side, BorderLayout.WEST);

        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setRowHeight(25);
        fileTable.getTableHeader().setReorderingAllowed(false);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(350);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(170);
        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });
        JScrollPane scroll = new JScrollPane(fileTable);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));
        addButton(bottom, "Open", this::openSelected);
        addButton(bottom, "New folder", this::createFolder);
        addButton(bottom, "Rename", this::renameSelected);
        addButton(bottom, "Copy", this::copySelected);
        addButton(bottom, "Move", this::moveSelected);
        addButton(bottom, "Delete", this::deleteSelected);
        addButton(bottom, "Properties", this::showProperties);
        addButton(bottom, "Toggle folder-first", this::sortRows);
        bottom.add(Box.createHorizontalStrut(10));
        bottom.add(new JLabel("Search:"));
        bottom.add(searchField);
        searchField.addActionListener(e -> searchCurrentFolder());
        addButton(bottom, "Find", this::searchCurrentFolder);
        addButton(bottom, "Refresh", () -> loadFolder(currentFolder));
        add(bottom, BorderLayout.SOUTH);

        getRootPane().setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
    }

    private void addQuickLink(JPanel panel, String label, Path path) {
        JButton button = new JButton(label);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        button.addActionListener(e -> {
            Path target = path == null ? findQuickFolder(label) : path;
            if (target == null) {
                JFileChooser chooser = new JFileChooser(Path.of(System.getProperty("user.home")).toFile());
                chooser.setDialogTitle("Choose your " + label + " folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                    target = chooser.getSelectedFile().toPath();
            }
            if (target != null) loadFolder(target);
        });
        panel.add(button);
    }

    /** Finds standard folders in the user profile, including common OneDrive layouts. */
    private Path findQuickFolder(String folderName) {
        Path windowsFolder = findWindowsKnownFolder(folderName);
        if (windowsFolder != null) return windowsFolder;

        List<Path> roots = new ArrayList<>();
        addRoot(roots, System.getenv("USERPROFILE"));
        addRoot(roots, System.getProperty("user.home"));
        addRoot(roots, System.getenv("OneDrive"));
        addRoot(roots, System.getenv("OneDriveCommercial"));
        addRoot(roots, System.getenv("OneDriveConsumer"));
        Path home = Path.of(System.getProperty("user.home"));
        try (var children = Files.list(home)) {
            children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("OneDrive"))
                    .forEach(roots::add);
        } catch (IOException | SecurityException ignored) { }
        for (Path root : roots) {
            Path candidate = root.resolve(folderName);
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }

    /** Uses Windows' own known-folder setting, including folders redirected to OneDrive. */
    private Path findWindowsKnownFolder(String folderName) {
        String specialFolder = switch (folderName) {
            case "Desktop" -> "DesktopDirectory";
            case "Documents" -> "MyDocuments";
            default -> null;
        };
        if (specialFolder == null || !System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))
            return null;
        try {
            String command = "[Environment]::GetFolderPath([Environment+SpecialFolder]::" + specialFolder + ")";
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!result.isEmpty()) {
                Path path = Path.of(result);
                if (Files.isDirectory(path)) return path;
            }
        } catch (IOException | InvalidPathException ignored) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private void addRoot(List<Path> roots, String value) {
        if (value != null && !value.isBlank()) {
            Path root = Path.of(value);
            if (!roots.contains(root)) roots.add(root);
        }
    }

    private void addButton(JPanel panel, String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(e -> action.run());
        panel.add(button);
    }

    /** Reads one directory and displays its contents in the table. */
    private void loadFolder(Path folder) {
        try {
            Path safePath = folder.toAbsolutePath().normalize();
            if (!Files.isDirectory(safePath)) {
                showError("That location is not a folder.");
                return;
            }
            currentFolder = safePath;
            locationField.setText(currentFolder.toString());
            searchField.setText("");
            tableModel.setRowCount(0);
            shownPaths.clear();
            List<Path> entries = new ArrayList<>();
            try (var stream = Files.list(currentFolder)) { stream.forEach(entries::add); }
            entries.sort((a, b) -> {
                if (foldersFirst) {
                    boolean ad = Files.isDirectory(a), bd = Files.isDirectory(b);
                    if (ad != bd) return ad ? -1 : 1;
                }
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            });
            for (Path entry : entries) addRow(entry);
        } catch (IOException | SecurityException ex) {
            showError("Could not read this folder:\n" + folder.toAbsolutePath().normalize() +
                    "\n\nDetails: " + ex.getClass().getSimpleName() +
                    (ex.getMessage() == null ? "" : "\n" + ex.getMessage()));
        }
    }

    private void addRow(Path entry) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
        String name = entry.getFileName() == null ? entry.toString() : entry.getFileName().toString();
        String type = attrs.isDirectory() ? "Folder" : getExtension(name);
        String size = attrs.isDirectory() ? "—" : formatSize(attrs.size());
        String modified = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(attrs.lastModifiedTime().toMillis()));
        shownPaths.add(entry);
        tableModel.addRow(new Object[]{(attrs.isDirectory() ? "📁  " : "📄  ") + name,
                type, size, modified});
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) + " file" : "File";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    private Path selectedPath() {
        int row = fileTable.getSelectedRow();
        return row >= 0 && row < shownPaths.size() ? shownPaths.get(row) : null;
    }

    private void openSelected() {
        Path path = selectedPath();
        if (path == null) { showError("Select a file or folder first."); return; }
        try {
            if (Files.isDirectory(path)) loadFolder(path);
            else if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(path.toFile());
            else showError("Opening files is not supported on this computer.");
        } catch (IOException | SecurityException ex) {
            showError("Could not open the selected item.\n" + ex.getMessage());
        }
    }

    private void goUp() {
        Path parent = currentFolder.getParent();
        if (parent != null) loadFolder(parent);
    }

    private void navigateToLocation() { loadFolder(Path.of(locationField.getText().trim())); }

    private void createFolder() {
        String name = JOptionPane.showInputDialog(this, "Enter a name for the new folder:", "New folder", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        if (!validName(name)) return;
        try { Files.createDirectory(currentFolder.resolve(name)); loadFolder(currentFolder); }
        catch (IOException | SecurityException ex) { showError("Could not create folder.\n" + ex.getMessage()); }
    }

    private void renameSelected() {
        Path source = selectedPath();
        if (source == null) { showError("Select an item to rename."); return; }
        String name = JOptionPane.showInputDialog(this, "New name:", source.getFileName().toString());
        if (name == null || name.isBlank()) return;
        if (!validName(name)) return;
        try { Files.move(source, source.resolveSibling(name)); loadFolder(currentFolder); }
        catch (IOException | SecurityException ex) { showError("Could not rename item.\n" + ex.getMessage()); }
    }

    private void copySelected() { transferSelected(false); }
    private void moveSelected() { transferSelected(true); }

    private void transferSelected(boolean move) {
        Path source = selectedPath();
        if (source == null) { showError("Select an item first."); return; }
        JFileChooser chooser = new JFileChooser(currentFolder.toFile());
        chooser.setDialogTitle("Choose destination folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath().resolve(source.getFileName());
        try {
            if (move) Files.move(source, destination);
            else if (Files.isDirectory(source)) copyFolder(source, destination);
            else Files.copy(source, destination);
            loadFolder(currentFolder);
        } catch (IOException | SecurityException ex) {
            showError("Could not " + (move ? "move" : "copy") + " item.\n" + ex.getMessage());
        }
    }

    /** Copies a folder and its contents recursively. */
    private void copyFolder(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(destination.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destination.resolve(source.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteSelected() {
        Path path = selectedPath();
        if (path == null) { showError("Select an item to delete."); return; }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete '" + path.getFileName() + "'? This cannot be undone.", "Confirm delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                        Files.delete(f); return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path d, IOException ex) throws IOException {
                        if (ex != null) throw ex; Files.delete(d); return FileVisitResult.CONTINUE;
                    }
                });
            } else Files.delete(path);
            loadFolder(currentFolder);
        } catch (IOException | SecurityException ex) { showError("Could not delete item.\n" + ex.getMessage()); }
    }

    private void searchCurrentFolder() {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) { loadFolder(currentFolder); return; }
        try {
            tableModel.setRowCount(0);
            shownPaths.clear();
            try (var stream = Files.list(currentFolder)) {
                for (Path entry : (Iterable<Path>) stream::iterator) {
                    if (entry.getFileName().toString().toLowerCase(Locale.ROOT).contains(query)) addRow(entry);
                }
            }
        } catch (IOException | SecurityException ex) { showError("Search failed.\n" + ex.getMessage()); }
    }

    private void sortRows() { foldersFirst = !foldersFirst; loadFolder(currentFolder); }

    private void showProperties() {
        Path path = selectedPath();
        if (path == null) { showError("Select an item first."); return; }
        try {
            BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class);
            String text = "Name: " + path.getFileName() + "\nLocation: " + path +
                    "\nType: " + (a.isDirectory() ? "Folder" : getExtension(path.toString())) +
                    "\nSize: " + (a.isDirectory() ? "—" : formatSize(a.size())) +
                    "\nLast modified: " + DateFormat.getDateTimeInstance().format(new Date(a.lastModifiedTime().toMillis()));
            JOptionPane.showMessageDialog(this, text, "Properties", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | SecurityException ex) { showError("Could not read properties.\n" + ex.getMessage()); }
    }

    private boolean validName(String name) {
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
            showError("Enter a valid single folder or file name."); return false;
        }
        return true;
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "NestView", JOptionPane.ERROR_MESSAGE);
    }

    /** Draws a small matching icon for the application title bar. */
    private Image createWindowIcon() {
        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(32, 32,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(new Color(48, 38, 95));
        g.fillRoundRect(0, 0, 32, 32, 8, 8);
        g.setColor(new Color(255, 205, 92));
        g.fillRoundRect(6, 11, 20, 14, 3, 3);
        g.fillRoundRect(7, 8, 10, 6, 2, 2);
        g.dispose();
        return icon;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { /* Use Java's default look and feel. */ }
            new NestView().setVisible(true);
        });
    }
}
