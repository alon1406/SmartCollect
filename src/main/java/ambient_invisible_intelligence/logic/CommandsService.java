package ambient_invisible_intelligence.logic;

import java.util.List;

import ambient_invisible_intelligence.boundaries.CommandBoundary;

public interface CommandsService {
	public List<Object> invokeCommand(CommandBoundary command, String userPassword);

    @Deprecated
    public List<CommandBoundary> getAllCommandsHistory(String userSystemID, String userEmail, String userPassword);

    public List<CommandBoundary> getAllCommandsHistory(String userSystemID, String userEmail,
                                               String userPassword, int size, int page);

    public void deleteAllCommands(String userSystemID, String userEmail, String userPassword);
}
