$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$AppName = "ohmyssh"
$VersionName = $env:OHMYSSH_VERSION_NAME
$VersionCode = $env:OHMYSSH_VERSION_CODE
$ReleaseTag = $env:OHMYSSH_RELEASE_TAG
if ([string]::IsNullOrWhiteSpace($VersionName)) {
    $propertiesPath = Join-Path $RootDir "gradle.properties"
    $versionLine = Get-Content -Path $propertiesPath | Where-Object { $_ -match '^ohmysshVersionName=([0-9A-Za-z._-]+)' } | Select-Object -First 1
    if ($versionLine -match '^ohmysshVersionName=([0-9A-Za-z._-]+)') {
        $VersionName = $Matches[1]
    }
}
if ([string]::IsNullOrWhiteSpace($VersionName)) {
    throw "App version not found. Set OHMYSSH_VERSION_NAME or ohmysshVersionName in gradle.properties."
}
$DistDir = Join-Path $RootDir "dist"
$OutFile = Join-Path $DistDir "$AppName`_$VersionName`_windows_x64.exe"
$RawZipFile = Join-Path $DistDir "$AppName`_$VersionName`_windows_x64_raw.zip"

$GradleWrapper = Join-Path $RootDir "gradlew.bat"
if (-not (Test-Path $GradleWrapper)) {
    throw "Gradle wrapper not found: $GradleWrapper"
}

New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

Write-Host "Building Windows release (jpackage app image with a bundled runtime)..."

$gradleArgs = @(
    ":shared:createDistributable",
    "-PohmysshVersionName=$VersionName",
    "-PohmysshReleaseTag=$ReleaseTag"
)
if (-not [string]::IsNullOrWhiteSpace($VersionCode)) {
    $gradleArgs += "-PohmysshVersionCode=$VersionCode"
}

Push-Location $RootDir
try {
    & $GradleWrapper @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

$BuildDir = Join-Path $RootDir "shared\build\compose\binaries\main\app\$AppName"
if (-not (Test-Path (Join-Path $BuildDir "$AppName.exe"))) {
    throw "Expected app binary not found: $(Join-Path $BuildDir "$AppName.exe")"
}

# Preserve the plain jpackage app image before creating the optional
# self-extracting executable. This archive contains the original release files.
if (-not ('System.IO.Compression.ZipFile' -as [type])) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
}
if (Test-Path $RawZipFile) {
    Remove-Item -Force $RawZipFile
}
[System.IO.Compression.ZipFile]::CreateFromDirectory(
    $BuildDir,
    $RawZipFile,
    [System.IO.Compression.CompressionLevel]::Optimal,
    $false)
Write-Host "Built raw Windows archive:"
Write-Host $RawZipFile

$TempDir = Join-Path ([System.IO.Path]::GetTempPath()) "$AppName-build-$PID"
$PayloadZip = Join-Path $TempDir "payload.zip"
$StubSource = Join-Path $TempDir "SelfExtractingLauncher.cs"
$StubExe = Join-Path $TempDir "stub.exe"
$Marker = [System.Text.Encoding]::ASCII.GetBytes("`r`n__OHMYSSH_PAYLOAD_BELOW__`r`n")

if (Test-Path $TempDir) {
    Remove-Item -Recurse -Force $TempDir
}
New-Item -ItemType Directory -Force -Path $TempDir | Out-Null

try {
    Copy-Item -Path $RawZipFile -Destination $PayloadZip -Force

    @'
using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

internal static class SelfExtractingLauncher
{
    private const string AppExe = "ohmyssh.exe";
    private const string SentinelFile = ".ohmyssh_payload_extracted";
    private static readonly byte[] Marker = Encoding.ASCII.GetBytes("\r\n__OHMYSSH_PAYLOAD_BELOW__\r\n");

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int MessageBox(IntPtr hWnd, string text, string caption, uint type);

    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            string selfPath = Assembly.GetExecutingAssembly().Location;
            string workDir = Path.Combine(Path.GetTempPath(), "ohmyssh-self-" + GetFileHashPrefix(selfPath));
            string appPath = Path.Combine(workDir, AppExe);
            string sentinelPath = Path.Combine(workDir, SentinelFile);

            if (!File.Exists(appPath) || !File.Exists(sentinelPath))
            {
                if (Directory.Exists(workDir))
                {
                    Directory.Delete(workDir, true);
                }
                Directory.CreateDirectory(workDir);
                ExtractPayload(selfPath, workDir);
            }

            ProcessStartInfo startInfo = new ProcessStartInfo(appPath)
            {
                UseShellExecute = false,
                WorkingDirectory = workDir,
                Arguments = JoinArguments(args)
            };

            using (Process process = Process.Start(startInfo))
            {
                process.WaitForExit();
                return process.ExitCode;
            }
        }
        catch (Exception ex)
        {
            MessageBox(IntPtr.Zero, ex.ToString(), "ohmyssh launch failed", 0x00000010);
            Console.Error.WriteLine(ex.ToString());
            return 1;
        }
    }

    private static void ExtractPayload(string selfPath, string workDir)
    {
        string payloadPath = Path.Combine(workDir, "payload.zip");
        using (FileStream input = File.OpenRead(selfPath))
        {
            long payloadStart = FindPayloadStart(input);
            if (payloadStart < 0)
            {
                throw new InvalidDataException("Embedded payload marker not found.");
            }

            input.Seek(payloadStart, SeekOrigin.Begin);
            using (FileStream output = File.Create(payloadPath))
            {
                input.CopyTo(output);
            }
        }

        ZipFile.ExtractToDirectory(payloadPath, workDir);
        File.Delete(payloadPath);
        File.WriteAllText(Path.Combine(workDir, SentinelFile), DateTime.UtcNow.ToString("O"));
    }

    // Streams the search instead of reading the whole file: the payload carries a
    // bundled Java runtime, so the executable is a few hundred megabytes.
    private static long FindPayloadStart(Stream stream)
    {
        int matched = 0;
        long position = 0;
        int read;
        while ((read = stream.ReadByte()) >= 0)
        {
            position++;
            if ((byte)read == Marker[matched])
            {
                matched++;
                if (matched == Marker.Length)
                {
                    return position;
                }
            }
            else
            {
                matched = (byte)read == Marker[0] ? 1 : 0;
            }
        }
        return -1;
    }

    private static string JoinArguments(string[] args)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.Length; i++)
        {
            if (i > 0)
            {
                builder.Append(' ');
            }
            builder.Append(QuoteArgument(args[i]));
        }
        return builder.ToString();
    }

    private static string QuoteArgument(string arg)
    {
        if (arg.Length == 0)
        {
            return "\"\"";
        }

        bool needsQuotes = arg.IndexOfAny(new[] { ' ', '\t', '\n', '\v', '"' }) >= 0;
        if (!needsQuotes)
        {
            return arg;
        }

        StringBuilder builder = new StringBuilder();
        builder.Append('"');
        int backslashes = 0;
        foreach (char c in arg)
        {
            if (c == '\\')
            {
                backslashes++;
                continue;
            }
            if (c == '"')
            {
                builder.Append('\\', backslashes * 2 + 1);
                builder.Append('"');
                backslashes = 0;
                continue;
            }
            builder.Append('\\', backslashes);
            builder.Append(c);
            backslashes = 0;
        }
        builder.Append('\\', backslashes * 2);
        builder.Append('"');
        return builder.ToString();
    }

    private static string GetFileHashPrefix(string path)
    {
        using (SHA256 sha256 = SHA256.Create())
        using (FileStream stream = File.OpenRead(path))
        {
            byte[] hash = sha256.ComputeHash(stream);
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++)
            {
                builder.Append(hash[i].ToString("x2"));
            }
            return builder.ToString();
        }
    }
}
'@ | Set-Content -Path $StubSource -Encoding ASCII

    $cscPath = "${env:SystemRoot}\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    if (-not (Test-Path $cscPath)) {
        $cscPath = "${env:SystemRoot}\Microsoft.NET\Framework\v4.0.30319\csc.exe"
    }
    if (-not (Test-Path $cscPath)) {
        throw "csc.exe not found. Cannot compile self-extracting stub."
    }
    & $cscPath /nologo /target:winexe /out:"$StubExe" /reference:"System.IO.Compression.dll" /reference:"System.IO.Compression.FileSystem.dll" "$StubSource"
    if ($LASTEXITCODE -ne 0) {
        throw "C# compilation failed with exit code $LASTEXITCODE."
    }

    $stubBytes = [System.IO.File]::ReadAllBytes($StubExe)
    $stream = [System.IO.File]::Create($OutFile)
    try {
        $stream.Write($stubBytes, 0, $stubBytes.Length)
        $stream.Write($Marker, 0, $Marker.Length)
        $payload = [System.IO.File]::OpenRead($PayloadZip)
        try {
            $payload.CopyTo($stream)
        }
        finally {
            $payload.Dispose()
        }
    }
    finally {
        $stream.Dispose()
    }
}
finally {
    Remove-Item -Recurse -Force $TempDir -ErrorAction SilentlyContinue
}

Write-Host "Built single-file executable:"
Write-Host $OutFile
