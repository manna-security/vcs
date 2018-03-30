package org.mannasecurity.webhooks;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.mannasecurity.domain.vcs.ProjectChange;
import org.mannasecurity.domain.ProjectMetadata;
import org.mannasecurity.domain.TaskRequest;
import org.mannasecurity.redis.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook")
public class CommitHook {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    RedisTemplate<String, TaskRequest> template;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public void onCommit(@RequestBody ProjectChange projectChange) {

        ObjectMapper mapperObj = new ObjectMapper();

        try {
            String jsonStr = mapperObj.writeValueAsString(projectChange);
            log.debug("Project change json: {}", jsonStr);
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Received git webhook request for ref {} before {} after {}",
            projectChange.getRef(), projectChange.getBefore(), projectChange.getAfter());

        TaskRequest taskRequest = new TaskRequest()
            .setProjectMetadata(
                new ProjectMetadata()
                    .setGuid(UUID.randomUUID().toString())
                    .setGitRepoUrl(projectChange.getRepository().getGitUrl())
                    .setOwnerName(projectChange.getRepository().getOwner().getLogin())
                    .setProjectName(projectChange.getRepository().getName())
                    .setProjectFullName(projectChange.getRepository().getFullName())
                    .setTimestamp(Instant.now())
            );

        template.opsForList().leftPush(Channel.CLONE_REQUEST.toString(), taskRequest);
    }

}