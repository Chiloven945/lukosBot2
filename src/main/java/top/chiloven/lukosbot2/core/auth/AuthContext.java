package top.chiloven.lukosbot2.core.auth;

public record AuthContext(
        boolean botAdmin,
        boolean chatAdmin
) {

    public boolean canManageChat() {
        return botAdmin || chatAdmin;
    }

    public boolean canManageGlobal() {
        return botAdmin;
    }

}
