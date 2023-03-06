package smartclide.projectimporter.controller;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import smartclide.projectimporter.application.ProjectImportService;

@RestController
@RequestMapping("/importProject")
public class ProjectImportController {
	Logger log = LoggerFactory.getLogger(ProjectImportController.class);
	
	@Autowired
	ProjectImportService importService;
	
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
			@Nullable @RequestParam("description") String description,
			@Nullable @RequestParam("visibility") String visibility,
			@RequestHeader String gitLabServerURL,
			@RequestHeader String gitlabToken) {
		
		try {
			URI createdRepoUri = importService.importProject(originalRepoUrl, name, description, visibility, gitLabServerURL, gitlabToken);
			return ResponseEntity.created(createdRepoUri).build();
		} catch (Exception e) {
			log.error("Exception during project import process. ", e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}
}
