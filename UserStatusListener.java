package chat_group;

public interface UserStatusListener {

    public void online(String login);
    public void offline(String login);
}
