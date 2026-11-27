# NestView

NestView is a small, explainable Java Swing file explorer for a student project. This revision has a new name and icon. Its Desktop and Documents shortcuts ask Windows for the actual special-folder paths first, which handles folders redirected into OneDrive. If Windows does not return an accessible folder, the app lets the user browse to the desired folder.

## Features

- Browse drives and folders with quick links and an address bar.
- Open files with their default Windows application.
- Create folders; rename, copy, move, and delete files or folders.
- Search the current folder, toggle folder-first ordering, and view file properties.
- Show the exact folder path and error detail if a folder cannot be read.

## Build the Windows installer

1. Use a Windows computer with a full JDK 17 or newer, including `javac` and `jpackage`.
2. Install WiX Toolset 3.x and make sure its `candle.exe` and `light.exe` commands are available in the same Command Prompt.
3. Open Command Prompt in the extracted NestView project folder.
4. Run `build-windows.bat`.
5. The installer should appear as `dist\NestView-3.0.0.exe`. Install it, then launch **NestView** from the Start menu.

The installer includes a Java runtime. Windows installers must be packaged on Windows.

## Suggested demonstration

Use a temporary folder and demonstrate navigation, folder creation, rename, search, properties, copy/move, and deletion of a disposable test file. Show Desktop or Documents quick access to demonstrate the Windows-known-folder lookup.
