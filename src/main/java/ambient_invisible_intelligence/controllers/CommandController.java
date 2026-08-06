package ambient_invisible_intelligence.controllers;


import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ambient_invisible_intelligence.boundaries.CommandBoundary;
import ambient_invisible_intelligence.logic.CommandsService;

@RestController
@RequestMapping(path = {"/ambient-invisible-intelligence/commands"}, version = "1.4+")
public class CommandController {
	
	private CommandsService commandService;

	public CommandController(CommandsService commandService) {
		this.commandService = commandService;
	}

	@PostMapping(
			consumes = {MediaType.APPLICATION_JSON_VALUE},
			produces = {MediaType.APPLICATION_JSON_VALUE})
	public Object[] invokeCommand(
	        @RequestParam(name = "userPassword", required = true) String userPassword,
	        @RequestBody CommandBoundary command) {
	    return this.commandService.invokeCommand(command, userPassword).toArray(new Object[0]);
	}
}