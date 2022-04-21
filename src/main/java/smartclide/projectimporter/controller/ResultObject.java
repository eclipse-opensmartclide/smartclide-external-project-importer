package smartclide.projectimporter.controller;

public class ResultObject {
	int status;
	String message;

	public ResultObject(int status, String message) {
		super();
		this.status = status;
		this.message = message;
	}
	
	public int getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
	
}
