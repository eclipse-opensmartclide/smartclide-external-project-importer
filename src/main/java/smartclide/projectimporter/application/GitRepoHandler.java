package smartclide.projectimporter.application;

import java.io.File;
import java.util.concurrent.Executors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smartclide.projectimporter.infra.ServiceCreationClient;

public class GitRepoHandler {
	Logger log = LoggerFactory.getLogger(ServiceCreationClient.class);
	
	private String repoUrl;
	private String workFolder;
	private boolean isSource;
	private CredentialsProvider credentialsProvider;
	private Git repo;

	public GitRepoHandler(String repoUrl, String workFolder, boolean isSource) {
		this.repoUrl = repoUrl;
		this.workFolder = workFolder;
		this.isSource = isSource;
	}
	
	public GitRepoHandler(String repoUrl, String workFolder, String gitlabToken, boolean isSource) {
		this(repoUrl, workFolder, isSource);
		this.credentialsProvider = new UsernamePasswordCredentialsProvider("gitlab-ci-token", gitlabToken);
	}
	
	public void cloneRepo() throws Exception {
		log.info("Cloning project from {}...", repoUrl);
		String checkoutFolder = isSource ? "source" : "destination";
		repo = Git.cloneRepository()
				.setURI(repoUrl)
				.setDirectory(new File(workFolder, checkoutFolder))
				.setCredentialsProvider(credentialsProvider)
				.call();
		log.info("Remote repository cloned at {}", getClonePath());
	}

	public void commitAndPush() throws Exception {
		if(this.isSource) throw new RuntimeException("Source repository cannot be modified");
		
		log.info("Adding contents to repo...");
		repo.add().addFilepattern(".").call();

		log.info("Creating commit...");
		repo.commit().setNoVerify(true).setMessage("Imported content").call();

		log.info("Pushing contents...");
		repo.push().setForce(true)
				.setCredentialsProvider(credentialsProvider)
				.setProgressMonitor(new TextProgressMonitor()).call();
		log.info("Done!");
	}
	
	public static String getProjectNameFromURL(String repoUrl) throws Exception {
		return new URIish(repoUrl).getHumanishName();
	}

	public String getClonePath() {
		return repo.getRepository().getWorkTree().getAbsolutePath();
	}
	
	public String getRepoURL() {
		return this.repoUrl;
	}


}
