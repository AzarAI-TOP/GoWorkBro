; GoWorkBro — Inno Setup Script
; Build: ISCC.exe goworkbro.iss
; Output version follows MyAppVersion below.

#define MyAppName "GoWorkBro"
#define MyAppVersion "1.0.2"
#define MyAppPublisher "AzarAI"
#define MyAppURL "https://github.com/AzarAI-TOP/GoWorkBro"
#define MyAppExeName "goworkbro.exe"

[Setup]
AppId={{8A3C2E1F-5B6D-4E7A-9C0F-3D2E1A5B6C7D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=..\..\build\installer
OutputBaseFilename=GoWorkBro-Setup-v{#MyAppVersion}
SetupIconFile=..\..\assets\icons\app_icon.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
ArchitecturesAllowed=x64compatible
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}
; Run uninstaller silently when upgrading
CloseApplications=force

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; Refresh the Windows icon cache so the taskbar/explorer show the new
; embedded icon right away after install or upgrade (fixes stale icons).
Filename: "{sys}\ie4uinit.exe"; Parameters: "-show"; Flags: runhidden nowait
; Launch app after install
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Kill the app before uninstalling (it may be in tray)
Filename: "taskkill"; Parameters: "/F /IM {#MyAppExeName}"; Flags: runhidden; RunOnceId: "KillApp"

[UninstallDelete]
; User data is deliberately preserved across upgrades/uninstalls.
