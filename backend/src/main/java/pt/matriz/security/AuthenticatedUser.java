package pt.matriz.security;

import java.io.Serializable;

/**
 * Principal stored in the session after login. Identity always comes from here, never from the
 * request body (Worksheet 03, Ex. 3.3). Only the id and the account role are kept; everything else
 * is read from the database on each request.
 */
public record AuthenticatedUser(long id, String accountRole) implements Serializable {
}
