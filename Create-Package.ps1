# Create the Microsoft Windows installation package
# and the default self-contained Jar package

# Change it!
$Version = "1.0"

Remove-Item -Recurse -Force .\package
mvn clean
mvn install
jpackage `
    --input .\target `
    --dest .\package `
    --main-class cloud.bernardi.pdfjuggler.MainFrame `
    --main-jar pdfjuggler-${Version}-jar-with-dependencies.jar `
    --icon graphics/icon-32.ico `
    --description "Sort, merge and cut the pages of PDF documents." `
    --copyright "Copyright 2020 Paolo Bernardi" `
    --name "PDF Juggler" `
    --app-version "${Version}" `
    --license-file LICENSE `
    --vendor "Paolo Bernardi" `
    --win-menu `
    --win-per-user-install
Copy-Item ".\target\pdfjuggler-${Version}-jar-with-dependencies.jar" .\package
Rename-Item ".\package\pdfjuggler-${Version}-jar-with-dependencies.jar" "pdfjuggler-${Version}.jar"
Rename-Item ".\package\PDF Juggler-${Version}.exe" "pdfjuggler-${Version}.exe"