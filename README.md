# smartclide-external-project-importer
A Spring service intended for importing external sources from remote git-based repositories.
The service uses the default Spring port 8080

This service is intended to interact with other services:
- Service creation
- (To be named) Service for creating Gitlab CI file

The docker image expects a parameter for inserting the Smartclide API URL, an environment variable called SMARTCLIDE_API_URL must be provided at launch time.

The service receives the following parameters:
- repoUrl: a Query parameter containing the url of the repository to be imported
- gitLabServerURL: a HEADER indicating the backing Smartclide Gitlab URL
- gitlabToken: a HEADER indicating the access token needed to write to Gitlab

The generated response includes the URL of the generated Gitlab repository