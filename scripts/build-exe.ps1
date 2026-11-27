#requires -Version 5
<#
.SYNOPSIS
  perm-admin Windows 单 exe 一键打包。

.DESCRIPTION
  流水线：npm run build（前端）→ mvn package（dist 打进 jar /static；permissions.json 打进 classpath 兜底）
  → jpackage app-image（内置 jlink 裁剪 JRE，目标机零依赖）
  → Enigma Virtual Box / 7z SFX 压成便携单 exe → 复制 perm-config skill/合并脚本/文档并打推荐分发 zip。

  产物（release/ 目录）：
    release\perm-admin-v<版本>-windows-x64.zip  推荐分发包：单 exe + 外置配置 + perm-config skill/合并脚本/文档
    release\perm-admin\                       app-image 目录形态（整目录可分发，含 perm-admin.exe 与 skill）
    release\perm-admin\permissions.json       外置默认配置（编辑后重启或 /api/config/reload 生效）
    release\perm-admin.exe                    便携单 exe（EVB 优先，缺则 7z SFX；文件名不可改）
    release\permissions.json                  单 exe 同款外置配置（与单 exe 同目录即生效）
    release\.agents\skills\perm-config\       随发布物附带的权限表接入 skill

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/build-exe.ps1
  powershell -ExecutionPolicy Bypass -File scripts/build-exe.ps1 -SkipFrontend
  powershell -ExecutionPolicy Bypass -File scripts/build-exe.ps1 -AppImageOnly
  powershell -ExecutionPolicy Bypass -File scripts/build-exe.ps1 -AppVersion 1.0.2
#>
param(
    [string]$JdkHome = "C:\Program Files\Eclipse Adoptium\jdk-26.0.1.8-hotspot",
    [string]$Maven   = "D:\WorkProject\.tools\apache-maven-3.9.16\bin\mvn.cmd",
    [string]$AppVersion = "",
    [switch]$SkipFrontend,
    [switch]$AppImageOnly
)

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$release = Join-Path $root 'release'
$appDir  = Join-Path $release 'perm-admin'
$env:JAVA_HOME = $JdkHome

if (-not $AppVersion) {
    [xml]$pomXml = Get-Content (Join-Path $root 'backend\pom.xml')
    $AppVersion = $pomXml.project.version
}

function JdkTool($name) { Join-Path $JdkHome "bin\$name.exe" }
function Step($msg) { Write-Host "`n=== $msg" -ForegroundColor Cyan }
function Require($path, $hint) { if (-not (Test-Path $path)) { throw "找不到: $path  ($hint)" } }

function Write-ReleaseReadme($targetDir) {
    $content = @'
# perm-admin 便携版 v{{VERSION}}

## 运行

1. 保持 `perm-admin.exe` 文件名不变，并确保它与 `permissions.json` 在同一目录。
2. 双击 `perm-admin.exe`；控制台窗口保持打开即服务运行，关闭即停止。
3. 浏览器访问 http://localhost:18917 （启动器通常会自动打开）。
4. 修改 `permissions.json` 后，重启程序或调用 `POST /api/config/reload`。

## 接入新项目/权限表

本包已经附带 `perm-config` skill 与配套工具：

- `.agents/skills/perm-config/SKILL.md`：接入权限表的标准流程和配置说明
- `scripts/add-perm-table.mjs`：把新权限表配置安全合并进 `permissions.json`（需要 Node.js 18+）
- `docs/design.md`：完整配置 Schema 与 API 契约

在本目录打开支持 skills 的 AI agent（如 Devin / Claude Code / Codex），直接说：

> 把项目 X 的权限表接入 perm-admin

如果 agent 没有自动发现 skill，先让它阅读 `.agents/skills/perm-config/SKILL.md` 并按其中步骤操作。便携包根目录下运行合并脚本时会默认使用本目录的 `permissions.json`；也可以显式加 `--config permissions.json`。
'@
    $content = $content.Replace('{{VERSION}}', $AppVersion)
    [IO.File]::WriteAllText((Join-Path $targetDir 'README.md'), $content, [System.Text.UTF8Encoding]::new($false))
}

function Copy-ReleaseSupportFiles($targetDir) {
    $skillSrcDir = Join-Path $root '.agents\skills\perm-config'
    $mergeScript = Join-Path $root 'scripts\add-perm-table.mjs'
    $designDoc = Join-Path $root 'docs\design.md'
    Require $skillSrcDir 'perm-config skill 缺失'
    Require $mergeScript '权限表合并脚本缺失'
    Require $designDoc '设计文档缺失'

    $skillTargetDir = Join-Path $targetDir '.agents\skills\perm-config'
    Get-ChildItem $skillSrcDir -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($skillSrcDir.Length).TrimStart('\')
        $dest = Join-Path $skillTargetDir $relative
        New-Item -ItemType Directory -Force (Split-Path -Parent $dest) | Out-Null
        Copy-Item $_.FullName $dest -Force
    }

    $scriptTargetDir = Join-Path $targetDir 'scripts'
    $docTargetDir = Join-Path $targetDir 'docs'
    New-Item -ItemType Directory -Force $scriptTargetDir | Out-Null
    New-Item -ItemType Directory -Force $docTargetDir | Out-Null
    Copy-Item $mergeScript (Join-Path $scriptTargetDir 'add-perm-table.mjs') -Force
    Copy-Item $designDoc (Join-Path $docTargetDir 'design.md') -Force
    Write-ReleaseReadme $targetDir
}

function Write-FilesToZip($sourceItems, $destinationPath) {
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $stream = [System.IO.File]::Open($destinationPath, [System.IO.FileMode]::Create)
    try {
        $zip = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach ($item in $sourceItems) {
                $itemInfo = Get-Item -LiteralPath $item
                if ($itemInfo.PSIsContainer) {
                    $base = $itemInfo.FullName
                    Get-ChildItem -LiteralPath $base -Recurse -File -Force | ForEach-Object {
                        $relative = $_.FullName.Substring($base.Length).TrimStart([IO.Path]::DirectorySeparatorChar)
                        $entryName = $itemInfo.Name + '/' + $relative.Replace([IO.Path]::DirectorySeparatorChar, [char]'/')
                        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
                    }
                } else {
                    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $itemInfo.FullName, $itemInfo.Name, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
                }
            }
        } finally { $zip.Dispose() }
    } finally { $stream.Dispose() }
}

# jdeps 实测清单 + jdeps 看不到的驱动运行时件（TLS/SASL/中文 locale/Windows 证书库）
$Modules = 'java.base,java.compiler,java.desktop,java.instrument,java.management,java.management.rmi,' +
           'java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,' +
           'java.sql,java.sql.rowset,java.transaction.xa,java.xml,' +
           'jdk.crypto.ec,jdk.crypto.mscapi,jdk.jfr,jdk.localedata,jdk.net,' +
           'jdk.security.auth,jdk.security.jgss,jdk.unsupported'

# ---------- 1. 前端 ----------
if (-not $SkipFrontend) {
    Step '1/5 前端 npm run build'
    Push-Location (Join-Path $root 'frontend')
    try { npm run build | Out-Host } finally { Pop-Location }
} else {
    Step '1/5 跳过前端构建 (-SkipFrontend)'
}

# ---------- 2. 后端 jar ----------
Step '2/5 后端 mvn package'
Require $Maven 'AGENTS.md 记录的 Maven 路径，可用 -Maven 覆盖'
& $Maven -f (Join-Path $root 'backend\pom.xml') -DskipTests -q package
if ($LASTEXITCODE -ne 0) { throw "mvn package 失败 (exit $LASTEXITCODE)" }

$jarPath = Join-Path $root "backend\target\perm-admin-$AppVersion.jar"
if (-not (Test-Path $jarPath)) { throw "未找到 backend\target\perm-admin-$AppVersion.jar（请确认 pom 版本或传 -AppVersion）" }
$jar = Get-Item $jarPath
Write-Host "jar: $($jar.FullName)  ($([math]::Round($jar.Length/1MB,1)) MB)"

$pkgInput = Join-Path $root 'backend\target\pkg-input'
if (Test-Path $pkgInput) { Remove-Item $pkgInput -Recurse -Force }
New-Item -ItemType Directory $pkgInput | Out-Null
Copy-Item $jar.FullName $pkgInput

# ---------- 3. jpackage app-image ----------
Step '3/5 jpackage app-image（jlink 裁剪运行时）'
Require (JdkTool 'jpackage') '需要 JDK14+ 的 jpackage，用 -JdkHome 指向完整 JDK'
if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
New-Item -ItemType Directory -Force $release | Out-Null

& (JdkTool 'jpackage') `
    --type app-image `
    --name perm-admin `
    --input $pkgInput `
    --main-jar $jar.Name `
    --dest $release `
    --app-version $AppVersion `
    --vendor perm-admin `
    --win-console `
    --add-modules $Modules `
    --jlink-options '--strip-debug --no-man-pages --no-header-files --include-locales=en,zh' `
    --java-options '-Dperm.openBrowser=true'
if ($LASTEXITCODE -ne 0) { throw "jpackage 失败 (exit $LASTEXITCODE)" }

# 外置默认配置：放 app-image exe 同目录（仅目录形态分发用；
# 单 exe 打包时会从 EVB/7z 包内排除 —— 包内虚拟副本会挡住用户放在 exe 旁的同名文件）
Copy-Item (Join-Path $root 'config\permissions.json') (Join-Path $appDir 'permissions.json') -Force
Copy-ReleaseSupportFiles $appDir
$appExe = Join-Path $appDir 'perm-admin.exe'
Require $appExe 'jpackage 未产出启动器'

if ($AppImageOnly) {
    Write-Host "`n完成（-AppImageOnly）：$appDir" -ForegroundColor Green
    return
}

# ---------- 4. 单 exe ----------
Step '4/5 打便携单 exe'
# 注意：文件名必须保持 perm-admin.exe —— jpackage 启动器按自身文件名找 app\<name>.cfg
$singleExe = Join-Path $release 'perm-admin.exe'

$evbConsole = @(
    "$env:ProgramFiles (x86)\Enigma Virtual Box\enigmavbconsole.exe",
    "${env:ProgramFiles(x86)}\Enigma Virtual Box\enigmavbconsole.exe",
    "$env:ProgramFiles\Enigma Virtual Box\enigmavbconsole.exe",
    (Get-Command enigmavbconsole.exe -ErrorAction SilentlyContinue).Source
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1

if ($evbConsole) {
    Write-Host "Enigma Virtual Box: $evbConsole"
    # 生成 .evb 工程（schema 参考 github.com/etiktin/generate-evb 模板，UTF-16LE 编码）：
    # %DEFAULT FOLDER% Type=3 容器 → 目录=Type3 节点嵌套 → 文件=Type2 节点（Name=basename）
    function ConvertTo-EvbNodes($dir) {
        $sb = [System.Text.StringBuilder]::new()
        Get-ChildItem $dir | Sort-Object { -not $_.PSIsContainer } | ForEach-Object {
            $name = [Security.SecurityElement]::Escape($_.Name)
            if ($_.PSIsContainer) {
                [void]$sb.Append("<File><Type>3</Type><Name>$name</Name><Action>0</Action>" +
                    '<OverwriteDateTime>false</OverwriteDateTime><OverwriteAttributes>false</OverwriteAttributes><Files>' +
                    (ConvertTo-EvbNodes $_.FullName) + '</Files></File>')
            } elseif ($_.FullName -ne $appExe -and $_.Name -ne 'permissions.json') {
                $path = [Security.SecurityElement]::Escape($_.FullName)
                [void]$sb.Append("<File><Type>2</Type><Name>$name</Name><File>$path</File>" +
                    '<ActiveX>false</ActiveX><ActiveXInstall>false</ActiveXInstall><Action>0</Action>' +
                    '<OverwriteDateTime>false</OverwriteDateTime><OverwriteAttributes>false</OverwriteAttributes>' +
                    '<PassCommandLine>false</PassCommandLine></File>')
            }
        }
        $sb.ToString()
    }
    $evbFile = Join-Path $release 'perm-admin.evb'
    $esc = { param($s) [Security.SecurityElement]::Escape($s) }
    $xml = '<?xml version="1.0" encoding="utf-16"?><>' +
        "<InputFile>$(&$esc $appExe)</InputFile><OutputFile>$(&$esc $singleExe)</OutputFile>" +
        '<Files><Enabled>true</Enabled><DeleteExtractedOnExit>true</DeleteExtractedOnExit>' +
        '<CompressFiles>true</CompressFiles><Files>' +
        '<File><Type>3</Type><Name>%DEFAULT FOLDER%</Name><Action>0</Action>' +
        '<OverwriteDateTime>false</OverwriteDateTime><OverwriteAttributes>false</OverwriteAttributes>' +
        '<Files>' + (ConvertTo-EvbNodes $appDir) + '</Files></File></Files></Files>' +
        '<Registries><Enabled>false</Enabled><Registries>' +
        ('Classes','User','Machine','Users','Config' | ForEach-Object {
            "<Registry><Type>1</Type><Virtual>true</Virtual><Name>$_</Name><ValueType>0</ValueType><Value/><Registries/></Registry>"
        } | Out-String).Trim() + '</Registries></Registries>' +
        '<Packaging><Enabled>false</Enabled></Packaging>' +
        # dll/exe 需落成真实临时文件供 Windows 加载，退出时清理
        '<Options><ShareVirtualSystem>false</ShareVirtualSystem>' +
        '<MapExecutableWithTemporaryFile>true</MapExecutableWithTemporaryFile>' +
        '<AllowRunningOfVirtualExeFiles>true</AllowRunningOfVirtualExeFiles></Options></>'
    [IO.File]::WriteAllText($evbFile, $xml, [Text.Encoding]::Unicode)

    & $evbConsole $evbFile
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $singleExe)) {
        Write-Warning 'enigmavbconsole 打包失败，可改用 release\perm-admin\ 目录形态，或用 EVB GUI 手动打包'
    }
} else {
    $szDir = "$env:ProgramFiles\7-Zip"
    if ((Test-Path "$szDir\7z.exe") -and (Test-Path "$szDir\7zCon.sfx")) {
        # 7z 自解压：每次启动解压到 %TEMP%（比 EVB 慢几秒），无需额外安装
        Write-Host '未检测到 Enigma Virtual Box，降级为 7z 自解压单 exe'
        $arc = Join-Path $release 'perm-admin.7z'
        $cfg = Join-Path $release 'perm-admin-sfx.txt'
        Push-Location $appDir
        try { & "$szDir\7z.exe" a -t7z -mx=5 -y $arc '*' '-x!permissions.json' | Out-Null } finally { Pop-Location }
        [IO.File]::WriteAllText($cfg,
            ";!@Install@!UTF-8!`r`nTitle=`"perm-admin`"`r`nRunProgram=`"perm-admin.exe`"`r`n;!@InstallEnd@!`r`n",
            [Text.Encoding]::UTF8)
        $bytes = [IO.File]::ReadAllBytes("$szDir\7zCon.sfx") +
                 [IO.File]::ReadAllBytes($cfg) +
                 [IO.File]::ReadAllBytes($arc)
        [IO.File]::WriteAllBytes($singleExe, $bytes)
        Remove-Item $arc, $cfg -Force
    } else {
        Write-Warning '未检测到 Enigma Virtual Box / 7-Zip SFX，跳过单 exe 打包。' `
            + '可 winget install Enigma.VirtualBox 后重跑，或直接分发 release\perm-admin\ 目录。'
    }
}

# 单 exe 旁也放一份默认可编辑配置，并补齐 skill/合并脚本/文档后打成推荐分发 zip
if (Test-Path $singleExe) {
    Copy-Item (Join-Path $root 'config\permissions.json') (Join-Path $release 'permissions.json') -Force
    Copy-ReleaseSupportFiles $release

    $zipPath = Join-Path $release "perm-admin-v$AppVersion-windows-x64.zip"
    $zipInputs = @(
        $singleExe,
        (Join-Path $release 'permissions.json'),
        (Join-Path $release 'README.md'),
        (Join-Path $release '.agents'),
        (Join-Path $release 'scripts'),
        (Join-Path $release 'docs')
    )
    Write-FilesToZip $zipInputs $zipPath
    $zip = Get-Item $zipPath
    Write-Host "分发包: $($zip.FullName)  ($([math]::Round($zip.Length/1MB,1)) MB)"
} else {
    Write-Warning '未生成单 exe，跳过便携 zip 打包。'
}

# ---------- 5. 汇总 ----------
Step '5/5 产物'
Get-ChildItem $release -Recurse -File |
    Where-Object {
        $_.Name -in @('perm-admin.exe', 'permissions.json', 'README.md', 'SKILL.md', 'add-perm-table.mjs', 'design.md') -or
        $_.Name -like 'perm-admin-v*-windows-x64.zip'
    } |
    ForEach-Object { '{0,10:N1} MB  {1}' -f ($_.Length/1MB), $_.FullName.Replace("$release\", '') }
Write-Host "`n用法：优先分发 release\perm-admin-v$AppVersion-windows-x64.zip；解压后双击 perm-admin.exe。也可整目录分发 release\perm-admin\。" -ForegroundColor Green
