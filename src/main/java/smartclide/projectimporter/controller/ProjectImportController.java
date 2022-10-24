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

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
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
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/importProject")
public class ProjectImportController {
	Logger log = LoggerFactory.getLogger(ProjectImportController.class);
	
	@Value("${projectCreationServiceUrl}")
	String PROJECT_CREATION_SERVICE_URL;
		
	@CrossOrigin(origins = "*")
	@PostMapping
	@Operation(summary = "Clones the given repository, creates a Gitlab project with the given name or one inferred from the project being imported"
			+ ", then pushes the original contents of main branch into a \"project_import\" branch in the newly created Gitlab repository")	
	@ApiResponses(value = {
	        @ApiResponse(responseCode = "201", description = "Project created and pushed into Gitlab", headers = {
	                @Header(name="Location", description = "The URL where the new project can be found")}),
	        @ApiResponse(responseCode = "500", description = "Error happened while processing the request", content = @Content)
	})
	public ResponseEntity<String> importProject(@RequestParam("repoUrl") String originalRepoUrl, 
			@Nullable @RequestParam("name") String name,
			@Nullable @RequestParam("visibility") String visibility,
			@RequestHeader String gitLabServerURL,
			@RequestHeader String gitlabToken) {
		
		String projectName = getProjectName(originalRepoUrl, name);

		try {
			Mono<ResultObject> newRemoteRequest = createRemoteRepo(gitLabServerURL, gitlabToken, projectName, visibility);//non-blocking
			Git repo = cloneServiceRepo(originalRepoUrl, projectName).get();//blocking
			log.info("Remote repository cloned at {}", repo.getRepository().getWorkTree().getAbsolutePath());
			
			String newRemoteUrl = getNewRemoteUrl(newRemoteRequest);
			
			setNewRemoteAndPush(repo, newRemoteUrl, gitlabToken);
			
			return ResponseEntity.created(new URI(newRemoteUrl)).build();
		} catch (Exception e) {
			log.error("Exception during project import process. ", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		} finally {
			cleanup(projectName);
		}
	}

	private String getNewRemoteUrl(Mono<ResultObject> newRemoteRequest) {
		ResultObject prjCreationResult = newRemoteRequest.block();//blocking
		String newRemoteUrl = null;
		if(prjCreationResult.status != 0) {
			log.error("Exception while creating GitLab project structure: {}", prjCreationResult.message);
			throw new RuntimeException(prjCreationResult.message);
		} else {
			newRemoteUrl = prjCreationResult.getMessage();	
			log.info("Project structure created at {}", newRemoteUrl);
		}
		return newRemoteUrl;
	}

	private String getProjectName(String originalRepoUrl, String name) {
		String projectName;
		if(name != null && !(name.isEmpty() || name.isBlank())) {
			projectName = name;
		}else {
			try {
				projectName = getProjectBaseName(originalRepoUrl);
			} catch (IllegalArgumentException | URISyntaxException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a git-based URL", e);
			}
		}
		return projectName;
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
		
		log.info("creating import branch...");
		repo.checkout()
		.setName("project_import")
		.setCreateBranch(true)
		.setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
		.call();

		log.info("Pushing contents...");
		repo.push().setForce(true)
		.setCredentialsProvider(new UsernamePasswordCredentialsProvider("gitlab-ci-token",gitlabToken))
		.setProgressMonitor(new TextProgressMonitor())
		.call();
		log.info("Done!");
	}
	
	private Future<Git> cloneServiceRepo(String originalRepoUrl, String projectName)
			throws GitAPIException, InvalidRemoteException, TransportException {
		log.info("Cloning project at {}...", originalRepoUrl);
		return Executors.newSingleThreadExecutor().submit(Git.cloneRepository().setURI(originalRepoUrl).setDirectory(new File(projectName)));
	}

	private Mono<ResultObject> createRemoteRepo(String gitLabServerURL, String gitlabToken, String projectName, String visibility) {
		log.info("Requesting project structure creation for project {}...", projectName);
		String projectVisibility;
		if(visibility != null && !(visibility.isEmpty() || visibility.isBlank())) {
			projectVisibility = visibility;
		}else {
			projectVisibility = "0";
		}
		WebClient client = WebClient.create();
		Mono<ResultObject> creationResult =  client.post()
		.uri(PROJECT_CREATION_SERVICE_URL)
		.header("projectName", projectName)
		.header("projVisibility", projectVisibility)
		.header("projDescription", "Imported project: "+ projectName)
		.header("gitLabServerURL", gitLabServerURL)
		.header("gitlabToken", gitlabToken)
		.retrieve().bodyToMono(ResultObject.class);
		return creationResult;
	}

	private String getProjectBaseName(String originalRepoUrl) throws IllegalArgumentException, URISyntaxException {
		return new URIish(originalRepoUrl).getHumanishName();
	}

}
