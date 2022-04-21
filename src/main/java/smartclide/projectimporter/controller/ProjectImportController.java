package smartclide.projectimporter.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
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
	
	@Value("$(smartclide.api.url)")
	String SMARTCLIDE_API_URL;
	
	String CICD_FILE_SERVICE_PATH = "/builds/ci-cd-file/";
	String CREATE_STRUCTURE_SERVICE_PATH = "/createStructure";
	
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
			
			File gitlabciFile = getCIFile(projectName, repo.getRepository().getDirectory());//blocking
			repo.add().addFilepattern(gitlabciFile.getPath()).call();
			repo.commit().setMessage("Chore: Add generated gitlab CI file").call();
			
			String newRemoteUrl = newRemoteRequest.block().getMessage();//blocking
			repo.remoteSetUrl().setRemoteUri(new URIish(newRemoteUrl));
			repo.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(gitlabToken, "" )).call();
			
			return ResponseEntity.created(new URI(newRemoteUrl)).build();
		} catch (Exception e) {
			e.printStackTrace();
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
		}
	}

	private File getCIFile(String projectName, File repoDir)
			throws IOException, MalformedURLException, FileNotFoundException {
		File gitlabciFile = new File(repoDir,"gitlab-ci.yaml");
		ReadableByteChannel readableByteChannel = Channels.newChannel(new URL(SMARTCLIDE_API_URL+CICD_FILE_SERVICE_PATH+projectName).openStream());
		FileOutputStream fileOutputStream = new FileOutputStream(gitlabciFile);
		try(fileOutputStream){
			fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
		}
		return gitlabciFile;
	}

	private Future<Git> cloneServiceRepo(String originalRepoUrl)
			throws GitAPIException, InvalidRemoteException, TransportException {
		
		return Executors.newSingleThreadExecutor().submit(Git.cloneRepository().setURI(originalRepoUrl));
	}

	private Mono<ResultObject> createRemoteRepo(String gitLabServerURL, String gitlabToken, String projectName) {
		WebClient client = WebClient.create();
		Mono<ResultObject> creationResult =  client.post()
		.uri(SMARTCLIDE_API_URL+CREATE_STRUCTURE_SERVICE_PATH)
		.header("projectName", projectName)
		.header("projVisibility", "0")
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
