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
package fr.jayblanc.mbyte.store.bot;

import fr.jayblanc.mbyte.store.data.exception.DataNotFoundException;
import fr.jayblanc.mbyte.store.data.exception.DataStoreException;
import fr.jayblanc.mbyte.store.files.FileService;
import fr.jayblanc.mbyte.store.files.exceptions.*;
import fr.jayblanc.mbyte.store.notification.NotificationServiceException;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST resource for receiving Discord messages from the Mbyte-bot.
 * Exposed at /api/bot/discord and authenticated via X-Bot-Token header.
 *
 * @author Jerome Blanchard
 */
@Path("bot/discord")
public class DiscordResource {

    private static final Logger LOGGER = Logger.getLogger(DiscordResource.class.getName());
    private static final String DISCORD_FOLDER = "discord";
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    @Inject FileService fileService;
    @Inject BotConfig botConfig;

    @POST
    @Transactional(Transactional.TxType.REQUIRED)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response receiveMessages(@HeaderParam("X-Bot-Token") String token, List<DiscordMessage> messages) {
        LOGGER.log(Level.INFO, "POST /api/bot/discord");

        if (!isValidToken(token)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        if (messages == null || messages.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\":\"messages list is empty\"}").build();
        }

        try {
            String discordFolderId = ensureDiscordFolder();
            String timestamp = FILE_DATE_FORMAT.format(Instant.now());
            String fileName = "discord-" + timestamp + ".txt";

            StringBuilder sb = new StringBuilder();
            for (DiscordMessage msg : messages) {
                sb.append("[").append(Instant.ofEpochMilli(msg.getTimestamp()).toString()).append("]");
                sb.append(" #").append(msg.getChannel());
                sb.append(" <").append(msg.getAuthor()).append(">");
                sb.append(" ").append(msg.getContent()).append("\n");
            }

            byte[] fileContent = sb.toString().getBytes(StandardCharsets.UTF_8);
            fileService.add(discordFolderId, fileName, new ByteArrayInputStream(fileContent));
            LOGGER.log(Level.INFO, "Discord messages stored as: {0}", fileName);

            return Response.status(Response.Status.CREATED).entity("{\"file\":\"" + fileName + "\"}").build();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error storing Discord messages", e);
            return Response.serverError().entity("{\"error\":\"internal server error\"}").build();
        }
    }

    /**
     * Ensures the discord folder exists at the root and returns its node ID.
     */
    private String ensureDiscordFolder() throws NodeNotFoundException, NodeAlreadyExistsException,
            NodeTypeException, NodePersistenceException, NotificationServiceException,
            DataStoreException, DataNotFoundException {
        try {
            fileService.add(FileService.ROOT_NODE_ID, DISCORD_FOLDER);
        } catch (NodeAlreadyExistsException e) {
            // folder already exists, that is fine
        }
        return fileService.list(FileService.ROOT_NODE_ID).stream()
                .filter(n -> DISCORD_FOLDER.equals(n.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("discord folder not found"))
                .getId();
    }

    /**
     * Performs a constant-time comparison of the provided token against the configured token
     * to prevent timing-based attacks.
     */
    private boolean isValidToken(String token) {
        if (token == null) {
            return false;
        }
        byte[] provided = token.getBytes(StandardCharsets.UTF_8);
        byte[] expected = botConfig.token().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(provided, expected);
    }
}
