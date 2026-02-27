/*
 * Copyright (C) 2025 Jerome Blanchard <jayblanc@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.jayblanc.mbyte.store.api.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.jayblanc.mbyte.store.api.dto.BotMessageDto;
import fr.jayblanc.mbyte.store.bot.BotConfig;
import fr.jayblanc.mbyte.store.files.FileService;
import fr.jayblanc.mbyte.store.files.entity.Node;
import fr.jayblanc.mbyte.store.files.exceptions.NodeAlreadyExistsException;
import fr.jayblanc.mbyte.store.files.exceptions.NodeNotFoundException;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST endpoint for the Discord bot to push messages into the owner's store.
 * Authentication is performed via the {@code X-Bot-Api-Key} header.
 *
 * @author Jerome Blanchard
 */
@Path("/bot")
public class BotResource {

    private static final Logger LOGGER = Logger.getLogger(BotResource.class.getName());
    static final String DISCORD_FOLDER = "discord";

    @Inject FileService fileService;
    @Inject BotConfig botConfig;
    @Inject ObjectMapper objectMapper;

    @POST
    @Path("/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response receiveMessage(@HeaderParam("X-Bot-Api-Key") String apiKey, BotMessageDto message) {
        LOGGER.log(Level.INFO, "POST /api/bot/messages");

        if (botConfig.apiKey().isEmpty() || !MessageDigest.isEqual(
                botConfig.apiKey().get().getBytes(StandardCharsets.UTF_8),
                apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8))) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (message == null || message.getMessageId() == null || message.getMessageId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("messageId is required").build();
        }

        try {
            String discordFolderId = getOrCreateDiscordFolder();
            String filename = message.getMessageId() + ".json";
            String json = objectMapper.writeValueAsString(message);
            String nodeId = fileService.add(discordFolderId, filename,
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            return Response.created(URI.create("/api/nodes/" + nodeId)).build();
        } catch (NodeAlreadyExistsException e) {
            LOGGER.log(Level.WARNING, "Message already stored: {0}", message.getMessageId());
            return Response.status(Response.Status.CONFLICT).build();
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Error serializing bot message", e);
            return Response.serverError().build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error storing bot message", e);
            return Response.serverError().build();
        }
    }

    private String getOrCreateDiscordFolder() throws Exception {
        Node root = fileService.get(FileService.ROOT_NODE_ID);
        List<Node> children = fileService.list(root.getId());
        Optional<Node> existing = children.stream()
                .filter(n -> DISCORD_FOLDER.equals(n.getName()) && n.isFolder())
                .findFirst();
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        try {
            return fileService.add(FileService.ROOT_NODE_ID, DISCORD_FOLDER);
        } catch (NodeAlreadyExistsException e) {
            // Created concurrently, fetch again
            List<Node> updatedChildren = fileService.list(root.getId());
            return updatedChildren.stream()
                    .filter(n -> DISCORD_FOLDER.equals(n.getName()) && n.isFolder())
                    .findFirst()
                    .orElseThrow(() -> new NodeNotFoundException("discord folder not found"))
                    .getId();
        }
    }

}
