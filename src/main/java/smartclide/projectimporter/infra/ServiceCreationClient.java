package smartclide.projectimporter.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
public class ServiceCreationClient {
	Logger log = LoggerFactory.getLogger(ServiceCreationClient.class);

	@Value("${projectCreationServiceUrl}")
	String PROJECT_CREATION_SERVICE_URL;

	public Mono<ResultObject> createRemoteRepoAsync(String gitLabServerURL, String gitlabToken, String projectName, String description, String visibility) {
		log.info("Requesting project structure creation for project {}...", projectName);
		
		WebClient client = WebClient.create();
		Mono<ResultObject> creationResult =  client.post()
		.uri(PROJECT_CREATION_SERVICE_URL)
		.header("projectName", projectName)
		.header("projVisibility", computeVisibilityValue(visibility))
		.header("projDescription", computeDescriptionValue(projectName, description))
		.header("gitLabServerURL", gitLabServerURL)
		.header("gitlabToken", gitlabToken)
		.retrieve().bodyToMono(ResultObject.class);
		return creationResult;
	}


	private String computeDescriptionValue(String projectName, String description) {
		String prjDescription;
		if(description.isBlank()) {
			prjDescription = "Imported project: "+ projectName;
		}else {
			prjDescription = description;
		}
		return prjDescription;
	}

	private String computeVisibilityValue(String visibility) {
		String projectVisibility;
		if(visibility != null && !(visibility.isEmpty() || visibility.isBlank())) {
			projectVisibility = visibility;
		}else {
			projectVisibility = "0";
		}
		return projectVisibility;
	}
	


}
