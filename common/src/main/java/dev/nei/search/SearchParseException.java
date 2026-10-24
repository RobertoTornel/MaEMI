package dev.nei.search;

public final class SearchParseException extends RuntimeException {
	private final int position;

	public SearchParseException(String message, int position) {
		super(message + " at " + position);
		this.position = position;
	}

	public int position() {
		return position;
	}
}
