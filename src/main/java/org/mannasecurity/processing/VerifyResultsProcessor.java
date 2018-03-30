package org.mannasecurity.processing;

import static org.mannasecurity.editor.BlockEditor.NEWLINE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.PullRequestMarker;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.RequestException;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.mannasecurity.domain.BlockDiff;
import org.mannasecurity.domain.FileDiff;
import org.mannasecurity.domain.ProjectDiff;
import org.mannasecurity.domain.ProjectMetadata;
import org.mannasecurity.domain.TaskRequest;
import org.mannasecurity.editor.ProjectEditor;
import org.mannasecurity.egit.DeletableRepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by jtmelton on 6/30/17.
 */
@Component
public class VerifyResultsProcessor implements TaskProcessor {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static final String MANNA_BOT_USERNAME = "manna-bot";

    private VcsActivityExecutor vcsActivityExecutor = new VcsActivityExecutor();

    private DeletableRepositoryService mannaRepositoryService =
        new DeletableRepositoryService(mannaClient());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProjectEditor editor = new ProjectEditor();

    @Value("${value.private.key.location:EMPTY}")
    private String privateKeyLocation;

    @Value("${value.manna.bot.oauth.token:EMPTY}")
    public String mannaBotOauthToken;

    @Override
    public void process(TaskRequest request) {
        ProjectMetadata metadata = request.getProjectMetadata();

        log.info("Received request in verify results processor covering project {} for url {}",
            metadata.getGuid(), metadata.getGitRepoUrl());

        if (hasOpenPullRequest(metadata)) {
            log.info("Stopping processing for project {} - an open PR already exists.",
                metadata.generateName());
            return;
        }

        if (hasFork(metadata)) {
            log.info("Fork already exists for project {} .", metadata.generateName());

            log.info("Deleting fork for project {} .", metadata.generateName());
            deleteFork(metadata);
        }

        fork(metadata);
        rename(metadata);
        Path clonedPath = clone(metadata);

        byte[] contentBytes = request.getContent();
        String contentJson = new String(contentBytes, StandardCharsets.UTF_8);
        ProjectDiff allDiff = null;
        try {
            allDiff = objectMapper.readValue(contentJson, ProjectDiff.class);
        } catch (IOException e) {
            log.error("Problem deserializing project diff from json string '" + contentJson + "'", e);
        }

        try {
            log.info("Editing files on disk as needed.");

            editor.edit(allDiff, clonedPath);

            for(FileDiff fileDiff : allDiff.getFileDiffs()) {
                log.info("Wrote changes to: {}", fileDiff.getRelativePath());
            }
        } catch (IOException e) {
            log.error("Problem editing file on disk.", e);
        }

        commit(clonedPath, allDiff);
        push(clonedPath, metadata);
        createPullRequest(metadata, allDiff);
    }

    private RepositoryId getMannaId(ProjectMetadata metadata) {
        return new RepositoryId(MANNA_BOT_USERNAME, forkName(metadata));
    }

    private RepositoryId getId(ProjectMetadata metadata) {
        return new RepositoryId(metadata.getOwnerName(), metadata.getProjectName());
    }

    private RepositoryId getMannaOriginalId(ProjectMetadata metadata) {
        return new RepositoryId(MANNA_BOT_USERNAME, metadata.getProjectName());
    }

    private String forkName(ProjectMetadata metadata) {
        return metadata.getOwnerName() + "_" + metadata.getProjectName();
    }

    private boolean hasOpenPullRequest(ProjectMetadata metadata) {
        boolean hasOpenPR = false;

        PullRequestService pullRequestService = new PullRequestService();

        try {
            List<PullRequest> pullRequests = pullRequestService.getPullRequests(
                getId(metadata), "open");

            for (PullRequest request : pullRequests) {
                if (MANNA_BOT_USERNAME.equals(request.getUser().getLogin())) {
                    hasOpenPR = true;
                    break;
                }
            }
        } catch (IOException e) {
            log.warn("Could not retrieve pull requests.", e);
        }

        return hasOpenPR;
    }

    private boolean hasFork(ProjectMetadata metadata) {
        boolean hasFork = false;

        RepositoryId mannaId = getMannaId(metadata);

        try {
            log.info("Checking if '{}' has a fork", mannaId);

            Repository fork = mannaRepositoryService.getRepository(mannaId);

            if(fork != null && fork.isFork()) {
                hasFork = true;
            }
        } catch (RequestException e) {
            if(404 == e.getStatus()) {
                log.info("Fork does not exist: {}", mannaId);
            }
            else {
                log.warn("Could not retrieve forks.", e);
            }
        } catch (IOException e) {
            log.warn("Could not retrieve forks.", e);
        }

        return hasFork;
    }

    private GitHubClient mannaClient() {
        return new GitHubClient()
            .setOAuth2Token(mannaBotOauthToken);
    }

    private void deleteFork(ProjectMetadata metadata) {
        try {
            RepositoryId mannaId = getMannaId(metadata);
            log.info("Deleting fork for '{}'", mannaId);

            Repository fork = mannaRepositoryService.getRepository(mannaId);

            if(fork != null && fork.isFork()) {
                System.err.println("deleting " + fork.getUrl());
                System.err.println("deleting " + fork.getOwner().getUrl() + " / " + fork.getOwner()
                    .getId() + " / " + fork.getOwner().getLogin());
                System.err.println("deleting " + fork.getGitUrl());
                System.err.println("deleting " + fork.getName());
                mannaRepositoryService.deleteRepository(fork);
            }
        } catch (IOException e) {
            log.warn("Could not delete forks.", e);
        }
    }

    private void fork(ProjectMetadata metadata) {
        try {
            RepositoryId id = getId(metadata);
            log.info("Forking '{}'", id);

            Repository repository = mannaRepositoryService.getRepository(id);

            if(repository != null && !repository.isFork()) {
                mannaRepositoryService.forkRepository(repository);
            }
        } catch (IOException e) {
            log.warn("Could not fork project.", e);
        }
    }

    private void rename(ProjectMetadata metadata) {
        try {
            RepositoryId id = getMannaOriginalId(metadata);
            String forkName = forkName(metadata);
            log.info("Renaming '{}' to '{}'", id, forkName);

            Repository repository = mannaRepositoryService.getRepository(id);

            // ensure we're only doing this on a fork
            if(repository != null && repository.isFork()) {
                Map<String, Object> values = new HashMap<>();
                values.put("name", forkName);
                mannaRepositoryService.editRepository(repository, values);
            }
        } catch (IOException e) {
            log.warn("Could not rename project.", e);
        }
    }

    private Path clone(ProjectMetadata metadata) {
        Path tempDir = null;

        try {
            log.info("Creating tmp dir");
            tempDir = Files.createTempDirectory("manna_tmp_dir");

            log.info("Cloning into tmp dir {}", tempDir);

            String gitUrl = "git@github.com:" + getMannaId(metadata) + ".git";
            vcsActivityExecutor.clone(tempDir, gitUrl);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            log.warn("Could not clone into temp dir.", e);
        }

        return tempDir;
    }

    private void commit(Path clonedPath, ProjectDiff allDiff) {
        try {
            log.info("Committing changes into tmp dir {}", clonedPath);

            StringBuffer detailedMessage = new StringBuffer();

            for(FileDiff fileDiff : allDiff.getFileDiffs()) {
                for(BlockDiff blockDiff : fileDiff.getBlockDiffs()) {
                    detailedMessage.append(NEWLINE).append(NEWLINE);
                    detailedMessage.append(blockDiff.getDescription());
                }
            }

            vcsActivityExecutor.commit(clonedPath, detailedMessage.toString());
        } catch (IOException | URISyntaxException | InterruptedException e) {
            log.warn("Could not commit into temp dir.", e);
        }
    }

    private void push(Path clonedPath, ProjectMetadata metadata) {
        try {
            log.info("Pushing changes to VCS {}", clonedPath);

            vcsActivityExecutor.push(clonedPath, metadata.getProjectFullName(), privateKeyLocation);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            log.warn("Could not commit into temp dir.", e);
        }
    }

    private void createPullRequest(ProjectMetadata metadata, ProjectDiff allDiff) {
        StringBuffer detailedMessage = new StringBuffer();

        for(FileDiff fileDiff : allDiff.getFileDiffs()) {
            for(BlockDiff blockDiff : fileDiff.getBlockDiffs()) {
                detailedMessage.append(NEWLINE).append(NEWLINE);
                detailedMessage.append(blockDiff.getDescription());
            }
        }

        PullRequest pullRequest = new PullRequest()
            .setTitle("[manna] Automated Security Analysis")
            .setHead(new PullRequestMarker().setLabel("manna-bot:master"))
            .setBase(new PullRequestMarker().setLabel("master"))
            .setBody(detailedMessage.toString());

        PullRequestService pullRequestService =
            new PullRequestService(mannaClient());

        try {
            pullRequestService.createPullRequest(getId(metadata), pullRequest);
        } catch (IOException e) {
            log.warn("Could not create pull request.", e);
        }
    }

}
