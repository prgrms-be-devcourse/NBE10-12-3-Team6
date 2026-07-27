package csh.back.domain.member.exception;

public class ExistingMemberException extends RuntimeException {
	public ExistingMemberException(String message) {
		super(message);
	}
}
