package org.mannasecurity.processing;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VcsActivityExecutor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public void clone(Path tempDir, String gitUrl)
    throws IOException, URISyntaxException, InterruptedException {
        String fileContent = Resources.toString(Resources.getResource("clone.sh"), Charsets.UTF_8);

        fileContent = fileContent.replace("VCS_URL_REPLACEMENT", gitUrl);
        fileContent = fileContent.replace("TEMP_DIR_REPLACEMENT", tempDir.toAbsolutePath().toString());

        Path cloneSh = tempDir.resolve("clone.sh");

        Files.write(cloneSh, fileContent.getBytes(StandardCharsets.UTF_8));

        log.info("Wrote clone.sh to {}", cloneSh.toString());

        executeAndLogScript(cloneSh);
    }

    public void commit(Path tempDir, String detailedCommitMessage)
        throws IOException, URISyntaxException, InterruptedException {
        String fileContent = Resources.toString(
            Resources.getResource("commit-all.sh"), Charsets.UTF_8);

        fileContent = fileContent.replace("TEMP_DIR_REPLACEMENT", tempDir.toAbsolutePath().toString());
        fileContent = fileContent.replace("DETAILED_MESSAGE", detailedCommitMessage);

        Path commitAllSh = tempDir.resolve("commit-all.sh");

        Files.write(commitAllSh, fileContent.getBytes(StandardCharsets.UTF_8));

        log.info("Wrote commit-all.sh to {}", commitAllSh.toString());

        executeAndLogScript(commitAllSh);
    }

    public void push(Path tempDir, String projectFullName, String privateKeyLocation)
        throws IOException, URISyntaxException, InterruptedException {
        String fileContent = Resources.toString(
            Resources.getResource("push.sh"), Charsets.UTF_8);

        fileContent = fileContent.replace("TEMP_DIR_REPLACEMENT", tempDir.toAbsolutePath().toString());
        fileContent = fileContent.replace("PROJECT_FULL_NAME", projectFullName);
        fileContent = fileContent.replace("PRIVATE_KEY_LOCATION", privateKeyLocation);

        Path pushSh = tempDir.resolve("push.sh");

        Files.write(pushSh, fileContent.getBytes(StandardCharsets.UTF_8));

        log.info("Wrote push.sh to {}", pushSh.toString());

        executeAndLogScript(pushSh);
    }

    private void executeAndLogScript(Path script)
        throws IOException, URISyntaxException, InterruptedException {
        String[] shellScript = new String[]{"/bin/bash", script.toAbsolutePath().toString()};
        Process p = Runtime.getRuntime().exec(shellScript);

        p.waitFor();

        // Grab output and print to display
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

        String line = "";
        while ((line = reader.readLine()) != null) {
            log.info(line);
        }
    }
}
