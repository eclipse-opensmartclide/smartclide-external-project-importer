package smartclide.projectimporter.controller;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/importProject")
public class ProjectImportController {
	Logger log = LoggerFactory.getLogger(ProjectImportController.class);
	
	@Value("${projectCreationServiceUrl}")
	String PROJECT_CREATION_SERVICE_URL;
		
	@CrossOrigin(origins = "*")
	@PostMapping
	public ResponseEntity<String> importProject(@RequestParam("repoUrl") String originalRepoUrl, @RequestHeader String gitLabServerURL,
			@RequestHeader String gitlabToken) {
		
		String projectName;
		try {
			projectName = getProjectBaseName(originalRepoUrl);
		} catch (IllegalArgumentException | URISyntaxException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a git-based URL", e);
		}

		try {
			Mono<ResultObject> newRemoteRequest = createRemoteRepo(gitLabServerURL, gitlabToken, projectName);//non-blocking
			Git repo = cloneServiceRepo(originalRepoUrl).get();//blocking
			log.info("Remote repository cloned at {}", repo.getRepository().getWorkTree().getAbsolutePath());
			
			String newRemoteUrl = newRemoteRequest.block().getMessage();//blocking
			setNewRemoteAndPush(repo, newRemoteUrl, gitlabToken);
			
			return ResponseEntity.created(new URI(newRemoteUrl)).build();
		} catch (Exception e) {
			e.printStackTrace();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		} finally {
			cleanup(projectName);
		}
	}

	private void cleanup(String projectName) {
		File checkoutDir = new File(projectName);
		if(checkoutDir.exists()) {
			try {
				Files.walk(checkoutDir.toPath())
				  .sorted(Comparator.reverseOrder())
				  .map(Path::toFile)
				  .forEach(File::delete);
				checkoutDir.delete();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void setNewRemoteAndPush(Git repo, String newRemoteUrl, String gitlabToken) throws Exception {
		log.info("Setting remote 'origin' to URL {}...", newRemoteUrl);
		repo.remoteSetUrl()
		.setRemoteName("origin")
		.setRemoteUri(new URIish(newRemoteUrl))
		.call();

		log.info("Pushing contents...");
		repo.push().setForce(true)
		.setCredentialsProvider(new UsernamePasswordCredentialsProvider("gitlab-ci-token",gitlabToken))
		.setProgressMonitor(new TextProgressMonitor())
		.call();
		log.info("Done!");
	}
	
	private Future<Git> cloneServiceRepo(String originalRepoUrl)
			throws GitAPIException, InvalidRemoteException, TransportException {
		log.info("Cloning project at {}...", originalRepoUrl);
		return Executors.newSingleThreadExecutor().submit(Git.cloneRepository().setURI(originalRepoUrl));
	}

	private Mono<ResultObject> createRemoteRepo(String gitLabServerURL, String gitlabToken, String projectName) {
		log.info("Requesting project structure creation for project {}...", projectName);
		WebClient client = WebClient.create();
		Mono<ResultObject> creationResult =  client.post()
		.uri(PROJECT_CREATION_SERVICE_URL)
		.header("projectName", projectName)
		.header("projVisibility", "0")
		.header("projDescription", "Imported project: "+ projectName)
		.header("gitLabServerURL", gitLabServerURL)
		.header("gitlabToken", gitlabToken)
		.retrieve().bodyToMono(ResultObject.class)
		.doOnSuccess(r -> log.info("Project structure created at {}", r.message));
		return creationResult;
	}

	private String getProjectBaseName(String originalRepoUrl) throws IllegalArgumentException, URISyntaxException {
		return new URIish(originalRepoUrl).getHumanishName();
	}

}
