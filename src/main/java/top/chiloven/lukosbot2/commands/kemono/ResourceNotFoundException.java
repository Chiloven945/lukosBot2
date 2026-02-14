package top.chiloven.lukosbot2.commands.kemono;

/**
 * Throw by the get method when the api return 404.
 */
class ResourceNotFoundException extends Exception {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
