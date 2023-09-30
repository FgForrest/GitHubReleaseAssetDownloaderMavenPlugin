# GitHub Release Asset Downloader

Maven plugin to download assets from GitHub releases.

## Usage

In your `pom.xml` the following plugin configuration will download the asset `Dist.zip` from the latest release (in time 
of plugin execution)
of the repository `lukashornych/evitalab` and saves it and extracts it to the directory `src/main/resources/META-INF/lab/gui`

```xml

<build>
    <plugins>
        <plugin>
            <groupId>one.edee.oss</groupId>
            <artifactId>github-release-asset-downloader-maven-plugin</artifactId>
            <version>1.0</version>
            <configuration>
                <owner>lukashornych</owner>
                <repo>evitalab</repo>
                <assetName>Dist.zip</assetName>
                <targetDir>${project.basedir}/src/main/resources/META-INF/lab/gui</targetDir>
            </configuration>
        </plugin>
    </plugins>
</build>
```

then you can manually run it using:

```bash
mvn one.edee:github-release-asset-downloader-maven-plugin:1.0-SNAPSHOT:download-asset
```

## Configuration

_Note_: currently only zip files are supported.

- `owner` - GitHub repository owner name
- `repo` - GitHub repository name
- `assetName` - entire name of the asset to download
- `targetDir` - directory where the content of the asset file will be extracted, all old files in the directory will be deleted before every extraction 

## Licence

[Apache License 2.0](LICENSE)

## Contribution

Any contributions are welcome and appreciated.