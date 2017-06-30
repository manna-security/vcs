package org.mannasecurity.vcs;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.mannasecurity.processing.CloneRequestProcessor;
import org.mannasecurity.processing.TaskProcessor;
import org.mannasecurity.processing.VerifyResultsProcessor;
import org.mannasecurity.redis.Channel;
import org.mannasecurity.redis.TaskProcessorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Created by jtmelton on 6/30/17.
 */
@Component
public class VcsManager {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final CloneRequestProcessor cloneRequestProcessor;

    private final VerifyResultsProcessor verifyResultsProcessor;

    private final TaskProcessorManager taskProcessorManager;

    @Autowired
    public VcsManager(final CloneRequestProcessor cloneRequestProcessor,
                      final VerifyResultsProcessor verifyResultsProcessor,
                      final TaskProcessorManager taskProcessorManager) {
        this.cloneRequestProcessor = cloneRequestProcessor;
        this.verifyResultsProcessor = verifyResultsProcessor;
        this.taskProcessorManager = taskProcessorManager;
    }

    @PostConstruct
    public void initialize() {
        Map<String, TaskProcessor> processorMap = new HashMap<>();
        processorMap.put(Channel.CLONE_REQUEST.toString(), cloneRequestProcessor);
        processorMap.put(Channel.VERIFY_RESULTS.toString(), verifyResultsProcessor);

        taskProcessorManager.setChannelProcessorMap(processorMap);

        taskProcessorManager.start();

        log.debug("Stopped vcs manager.");
    }

    @PreDestroy
    public void shutdown() {
        taskProcessorManager.stop();

        log.debug("Stopped vcs manager.");
    }

}
