package org.mannasecurity.processing;

import static org.mannasecurity.domain.ProjectMetadata.renew;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.mannasecurity.compression.Tarball;
import org.mannasecurity.domain.TaskRequest;
import org.mannasecurity.redis.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

/**
 * Created by jtmelton on 6/30/17.
 */
@Component
public class CloneRequestProcessor implements TaskProcessor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private VcsActivityExecutor vcsActivityExecutor = new VcsActivityExecutor();

    @Autowired
    RedisTemplate<String, TaskRequest> template;

    @Override
    public void process(TaskRequest request) {
        log.info("Received request in clone request processor covering project {} for url {}",
            request.getProjectMetadata().getGuid(), request.getProjectMetadata().getGitRepoUrl());

        try {
            log.info("Creating tmp dir");
            Path tempDir = Files.createTempDirectory("manna_tmp_dir");

            log.info("Cloning into tmp dir {}", tempDir);
            vcsActivityExecutor.clone(tempDir, request.getProjectMetadata().getGitRepoUrl());

            log.info("Tarballing tmp dir {}", tempDir);
            String tarballName = tempDir.toAbsolutePath().toString() + "/code.tar.gz";
            Tarball.compress(tempDir.resolve("code"), new File(tarballName));

            byte[] tarballBytes = Files.readAllBytes(Paths.get(tarballName));

            TaskRequest taskRequest = new TaskRequest()
                .setProjectMetadata(renew(request.getProjectMetadata()))
                .setContent(tarballBytes);

            log.info("Sending scan request for guid {} and gitUrl {}",
                taskRequest.getProjectMetadata().getGuid(),
                taskRequest.getProjectMetadata().getGitRepoUrl());

            template.opsForList().leftPush(Channel.SCAN_REQUEST.toString(), taskRequest);

            log.info("Removing tmp dir {}", tempDir);
            FileSystemUtils.deleteRecursively(tempDir);
        } catch(IOException | URISyntaxException | InterruptedException e) {
            log.error("Error processing clone request.", e);
        }

    }

}
