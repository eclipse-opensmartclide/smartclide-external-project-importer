package smartclide.projectimporter.application;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import smartclide.projectimporter.infra.ResultObject;
import smartclide.projectimporter.infra.ServiceCreationClient;

@Service
public class ProjectImportService {
	Logger log = LoggerFactory.getLogger(ProjectImportService.class);

	@Autowired
	ServiceCreationClient serviceCreation;

	public URI importProject(String originalRepoUrl,String projectName,String description,String visibility,String gitLabServerURL,String gitlabToken) {
		String workFolder = UUID.randomUUID().toString();

		try {
			
			Mono<ResultObject> newRemoteRequest = serviceCreation.createRemoteRepoAsync(gitLabServerURL, gitlabToken, projectName, description, visibility);//non-blocking
			GitRepoHandler sourceRepoHandler = new GitRepoHandler(originalRepoUrl, workFolder, true);
			sourceRepoHandler.cloneRepo();//blocking
			
			GitRepoHandler destinationRepoHandler = new GitRepoHandler(getNewRemoteUrl(newRemoteRequest), workFolder, gitlabToken, false);
			destinationRepoHandler.cloneRepo();
			
			copyContents(sourceRepoHandler.getClonePath(), destinationRepoHandler.getClonePath());
			destinationRepoHandler.commitAndPush();
			
			return new URI(destinationRepoHandler.getRepoURL());
		} catch (Exception e) {
			log.error("Exception during project import process. ", e);
			throw new RuntimeException(e);
		} finally {
			cleanup(workFolder);
		}
	}

	private String getNewRemoteUrl(Mono<ResultObject> newRemoteRequest) {
		ResultObject prjCreationResult = newRemoteRequest.block();//blocking
		String newRemoteUrl = null;
		if(prjCreationResult.getStatus() != 0) {
			log.error("Exception while creating GitLab project structure: {}", prjCreationResult.getMessage());
			throw new RuntimeException(prjCreationResult.getMessage());
		} else {
			newRemoteUrl = prjCreationResult.getMessage();	
			log.info("Project structure created at {}", newRemoteUrl);
		}
		return newRemoteUrl;
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
	
	private void copyContents(String sources, String destination) throws IOException {
		Path sourceFolder = Paths.get(sources);
	    Path destinationFolder = Paths.get(destination);
	    
		Files.walk(sourceFolder).forEach(sourcePath -> {
			Path destPath = destinationFolder.resolve(sourceFolder.relativize(sourcePath));
			File destFile = destPath.toFile();
			if(!destFile.exists()) {
				try {
					Files.copy(sourcePath, destPath);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

}
