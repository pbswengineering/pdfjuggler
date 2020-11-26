# Update the version number in pom,xml, Const.java and Create-Package.ps1
if ($args.Length -lt 2) {
    Write-Output "Update PDF Juggler version in pom.xml, Const.java and Create-Package.ps1`n"
    Write-Output "Usage: Update-Version.ps1 <old_version> <new_version>`n"
    Write-Output "       old_version: the old version (usually in x.y format)"
    Write-Output "       new_version: the new version (usually in x.y format)`n"
    exit
}
$oldVersion = $args[0]
$newVersion = $args[1]
Function Update-File ($file, $original, $replacement)
{
    Write-Output "Updating $file from ${oldVersion} to ${newVersion}"
    (Get-Content $file).replace($original, $replacement) | Set-Content $file
}

Update-File pom.xml `
            "<version>$oldVersion</version><!-- Main version -->" `
            "<version>$newVersion</version><!-- Main version -->"
Update-File src/main/java/cloud/bernardi/pdfjuggler/Const.java `
            "public static final String VERSION = `"$oldVersion`";" `
            "public static final String VERSION = `"$newVersion`";"
Update-File Create-Package.ps1 `
            "`$Version = `"$oldVersion`"" `
            "`$Version = `"$newVersion`""