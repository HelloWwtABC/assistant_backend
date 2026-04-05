package com.wwt.assistant.common;

public final class UserContextHolder {

    private static final ThreadLocal<CurrentUser> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void setCurrentUser(CurrentUser currentUser) {
        CONTEXT.set(currentUser);
    }

    public static CurrentUser getCurrentUser() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        CurrentUser currentUser = getCurrentUser();
        return currentUser == null ? null : currentUser.getUserId();
    }

    public static String getUsername() {
        CurrentUser currentUser = getCurrentUser();
        return currentUser == null ? null : currentUser.getUsername();
    }

    public static Long getTeamId() {
        CurrentUser currentUser = getCurrentUser();
        return currentUser == null ? null : currentUser.getTeamId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
