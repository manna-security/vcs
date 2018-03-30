package org.mannasecurity.egit;

import java.io.IOException;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.RepositoryService;

public class DeletableRepositoryService extends RepositoryService {

    public DeletableRepositoryService() { }

    public DeletableRepositoryService(GitHubClient client) {
        super(client);
    }

    public void deleteRepository(IRepositoryIdProvider repository) throws IOException {
        String id = this.getId(repository);
        StringBuilder uri = new StringBuilder("/repos");
        uri.append('/').append(id);
        System.err.println("deleting " + uri.toString());
        this.client.delete(uri.toString());
    }

}
