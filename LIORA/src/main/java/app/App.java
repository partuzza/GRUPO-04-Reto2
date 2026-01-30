package app;

import static spark.Spark.*;
import java.sql.*;

public class App {

    private static final String DB_URL = "jdbc:mysql://mysql-8001.dinaserver.com/Liora?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "Liora";
    private static final String DB_PASS = "Liora@2026";

    public static void main(String[] args) {
        port(4567);

        // Servir HTML desde /public
        staticFiles.location("/public");

        // --------------------
        // Página inicial
        // --------------------
        get("/", (req, res) -> {
            res.redirect("/index.html");
            return null;
        });

        // --------------------
        // Registro de cliente
        // --------------------
        post("/register", (req, res) -> {
            String nombre = req.queryParams("nombreCLI");
            String apellido = req.queryParams("apellidoCLI");
            String fecha_nac = req.queryParams("fecha_nacCLI");
            String dni = req.queryParams("dniCLI");
            String telefono = req.queryParams("telefonoCLI");
            String email = req.queryParams("emailCLI");
            String usuario = req.queryParams("usuarioCLI");
            String contrasena = req.queryParams("contrasenaCLI");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String sql = "INSERT INTO CLIENTE(nombreCLI, apellidoCLI, fecha_nacCLI, dniCLI, telefonoCLI, correoCLI, usuarioCLI, contrasenaCLI) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, nombre);
                ps.setString(2, apellido);
                ps.setDate(3, java.sql.Date.valueOf(fecha_nac));
                ps.setString(4, dni);
                ps.setString(5, telefono);
                ps.setString(6, email);
                ps.setString(7, usuario);
                ps.setString(8, contrasena);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                return "Error al registrar usuario: " + e.getMessage();
            }

            res.redirect("/index.html");
            return null;
        });

        // --------------------
        // Login cliente
        // --------------------
        post("/loginCLI", (req, res) -> {
            String usuario = req.queryParams("usuarioCLI");
            String contrasena = req.queryParams("contrasenaCLI");

            if (validarCliente(usuario, contrasena)) {
                res.redirect("/formu.html?usuarioCLI=" + usuario);
            } else {
                return "Usuario o contraseña incorrectos (CLI)";
            }
            return null;
        });

        // --------------------
        // Login trabajador
        // --------------------
        post("/loginTRA", (req, res) -> {
            String usuario = req.queryParams("usuarioTRA");
            String contrasena = req.queryParams("contrasenaTRA");

            String tipo = validarTrabajadorTipo(usuario, contrasena);
            if (tipo != null) {
                if (tipo.equals("ADMIN")) {
                    res.redirect("/panelAdmin");
                } else {
                    res.redirect("/misTrabajos?usuarioTRA=" + usuario);
                }
            } else {
                return "Usuario o contraseña incorrectos (TRA)";
            }
            return null;
        });
// --------------------
// Reserva de servicios
// --------------------
post("/reservaPack", (req, res) -> {
    String nombre = req.queryParams("nombreCLI");
    String[] servicios = req.queryParamsValues("servicios_pack[]");
    String fecha = req.queryParams("fecha_reserva");

    if (fecha != null) fecha = fecha.replace("T", " "); // datetime-local fix

    int usuarioId = obtenerClienteIdPorNombre(nombre);

    if (usuarioId > 0) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            if (servicios != null) {
                for (String s : servicios) {
                    if(s!= null){
                  int idServ = obtenerServicioIdPorNombre(s, conn);
Integer idTRA = null;
if (idServ > 0) {
    idTRA = obtenerTrabajadorAsignado(conn, idServ);
}

                    // --- DEPURACIÓN ---
                    System.out.println("Reserva de servicio: " + s + " -> idServ=" + idServ + ", idTRA=" + idTRA);

                    String sqlReser = "INSERT INTO RESERVA (fecha_reser, idCLI, idTRA) VALUES (?, ?, ?)";
                    PreparedStatement psReser = conn.prepareStatement(sqlReser, Statement.RETURN_GENERATED_KEYS);
                    psReser.setTimestamp(1, java.sql.Timestamp.valueOf(fecha + ":00"));
                    psReser.setInt(2, usuarioId);

                    if (idTRA != null) {
                        psReser.setInt(3, idTRA);
                    } else {
                        psReser.setNull(3, java.sql.Types.INTEGER);
                    }

                    psReser.executeUpdate();

                    ResultSet rsKeys = psReser.getGeneratedKeys();
                    if (rsKeys.next()) {
                        int reservaId = rsKeys.getInt(1);
                        if (idServ > 0) {
                            String sqlCont = "INSERT INTO CONTIENE_2 (id_reser, id_serv) VALUES (?, ?)";
                            PreparedStatement psCont = conn.prepareStatement(sqlCont);
                            psCont.setInt(1, reservaId);
                            psCont.setInt(2, idServ);
                            psCont.executeUpdate();
                        }
                    }
                }}
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return "Error al registrar reserva: " + e.getMessage();
        }
    } else {
        return "Cliente no encontrado";
    }

    // ---- HTML generado ----
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Reserva Correcta</title>");
    html.append("<link rel='stylesheet' type='text/css' href='/panel.css'>");
    html.append("</head><body>");
    html.append("<h1>¡RESERVA CORRECTA!</h1>");
    html.append("<p><strong>Cliente:</strong> ").append(nombre).append("</p>");
    html.append("<p><strong>Tipo de reserva:</strong> Servicios</p>");

    html.append("<p><strong>Servicios:</strong> ");
    if (servicios != null) {
        for (int i = 0; i < servicios.length; i++) {
            html.append(servicios[i]);
            if (i < servicios.length - 1) html.append(", ");
        }
    }
    html.append("</p>");

    html.append("<p><strong>Fecha de reserva:</strong> ").append(fecha).append("</p>");
    html.append("<br><a href='/index.html'><button>Volver al inicio</button></a>");
    html.append("</body></html>");

    res.type("text/html");
    return html.toString();
});


        // --------------------
        // Reserva de plan
        // --------------------
        post("/reservaPlan", (req, res) -> {
            String nombre = req.queryParams("nombreCLI");
            String plan = req.queryParams("plan_mensual");
            String fecha_ini = req.queryParams("fecha_ini");

            int usuarioId = obtenerClienteIdPorNombre(nombre);
            if (usuarioId <= 0 || plan == null || fecha_ini == null) {
                return "Datos del plan incorrectos";
            }

            String fechaFin = null;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String sql = "INSERT INTO SUSCRIPCION(idCLI, tipo_sus, fecha_ini_sus, fecha_fin_sus) " +
                        "VALUES (?, ?, ?, DATE_ADD(?, INTERVAL 1 MONTH))";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, usuarioId);
                ps.setString(2, plan);
                ps.setDate(3, java.sql.Date.valueOf(fecha_ini));
                ps.setDate(4, java.sql.Date.valueOf(fecha_ini));
                ps.executeUpdate();

                PreparedStatement ps2 = conn.prepareStatement("SELECT DATE_ADD(?, INTERVAL 1 MONTH)");
                ps2.setDate(1, java.sql.Date.valueOf(fecha_ini));
                ResultSet rs = ps2.executeQuery();
                if (rs.next()) fechaFin = rs.getString(1);
            } catch (SQLException e) {
                e.printStackTrace();
                return "Error al registrar el plan: " + e.getMessage();
            }

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Suscripción Correcta</title>");
            html.append("<link rel='stylesheet' type='text/css' href='/panel.css'>");
            html.append("</head><body>");
            html.append("<h1>¡SUSCRIPCIÓN CORRECTA!</h1>");
            html.append("<p><strong>Usuario:</strong> ").append(nombre).append("</p>");
            html.append("<p><strong>Plan:</strong> ").append(plan).append("</p>");
            html.append("<p><strong>Fecha inicio:</strong> ").append(fecha_ini).append("</p>");
            html.append("<p><strong>Fecha fin:</strong> ").append(fechaFin).append("</p>");
            html.append("<br><a href='/index.html'><button>Volver al inicio</button></a>");
            html.append("</body></html>");

            res.type("text/html");
            return html.toString();
        });

        // --------------------
        // Panel Admin completo
        // --------------------
        get("/panelAdmin", (req, res) -> {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Panel Admin SPA</title>");
            html.append("<link rel='stylesheet' type='text/css' href='/panel.css'>");
            html.append("</head><body>");
            html.append("<div class='main-wrapper'>");
            html.append("<h1>Panel de Administración SPA</h1>");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                Statement st = conn.createStatement();

                // Resumen general
                html.append("<div class='section'><h2>Resumen general</h2>");
                ResultSet rs = st.executeQuery("SELECT COUNT(*) AS totalClientes FROM CLIENTE");
                if (rs.next()) html.append("<p>Total de clientes: ").append(rs.getInt("totalClientes")).append("</p>");
                rs = st.executeQuery("SELECT COUNT(*) AS totalTrabajadores FROM TRABAJADOR");
                if (rs.next()) html.append("<p>Total de trabajadores: ").append(rs.getInt("totalTrabajadores")).append("</p>");
                rs = st.executeQuery("SELECT COUNT(*) AS totalReservas FROM RESERVA");
                if (rs.next()) html.append("<p>Total de reservas: ").append(rs.getInt("totalReservas")).append("</p>");
                rs = st.executeQuery("SELECT COUNT(*) AS totalPlanes FROM SUSCRIPCION WHERE fecha_fin_sus >= NOW()");
                if (rs.next()) html.append("<p>Total de planes activos: ").append(rs.getInt("totalPlanes")).append("</p>");
                html.append("</div>");

                // Servicios más reservados
                html.append("<div class='section'><h2>Servicios más reservados (TOP 5)</h2>");
                html.append("<table><tr><th>Servicio</th><th>Total reservas</th></tr>");
                rs = st.executeQuery(
                        "SELECT S.tipo_serv, COUNT(*) AS total " +
                                "FROM CONTIENE_2 CT " +
                                "JOIN SERVICIO S ON CT.id_serv = S.id_serv " +
                                "GROUP BY S.tipo_serv " +
                                "ORDER BY total DESC " +
                                "LIMIT 5"
                );
                while (rs.next()) {
                    html.append("<tr><td>").append(rs.getString("tipo_serv")).append("</td>");
                    html.append("<td>").append(rs.getInt("total")).append("</td></tr>");
                }
                html.append("</table></div>");

                // Próximas reservas
                html.append("<div class='section'><h2>Próximas reservas (TOP 10)</h2>");
                html.append("<table><tr><th>Cliente</th><th>Servicio</th><th>Fecha</th><th>Trabajador</th></tr>");
                rs = st.executeQuery(
                        "SELECT CONCAT(C.nombreCLI,' ',C.apellidoCLI) AS cliente, S.tipo_serv, R.fecha_reser, " +
                                "IFNULL(CONCAT(T.nombreTRA,' ',T.apellidoTRA),'No asignado') AS trabajador " +
                                "FROM RESERVA R " +
                                "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                                "JOIN CONTIENE_2 CT ON R.id_reser = CT.id_reser " +
                                "JOIN SERVICIO S ON CT.id_serv = S.id_serv " +
                                "LEFT JOIN TRABAJADOR T ON R.idTRA = T.idTRA " +
                                "WHERE R.fecha_reser >= NOW() " +
                                "ORDER BY R.fecha_reser ASC " +
                                "LIMIT 10"
                );
                while (rs.next()) {
                    html.append("<tr><td>").append(rs.getString("cliente")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_serv")).append("</td>");
                    html.append("<td>").append(rs.getTimestamp("fecha_reser")).append("</td>");
                    html.append("<td>").append(rs.getString("trabajador")).append("</td></tr>");
                }
                html.append("</table></div>");

                // Clientes más activos
                html.append("<div class='section'><h2>Clientes más activos (TOP 5)</h2>");
                html.append("<table><tr><th>Cliente</th><th>Total reservas</th></tr>");
                rs = st.executeQuery(
                        "SELECT CONCAT(C.nombreCLI,' ',C.apellidoCLI) AS cliente, COUNT(R.id_reser) AS total " +
                                "FROM CLIENTE C " +
                                "LEFT JOIN RESERVA R ON C.idCLI = R.idCLI " +
                                "GROUP BY C.idCLI " +
                                "ORDER BY total DESC " +
                                "LIMIT 5"
                );
                while (rs.next()) {
                    html.append("<tr><td>").append(rs.getString("cliente")).append("</td>");
                    html.append("<td>").append(rs.getInt("total")).append("</td></tr>");
                }
                html.append("</table></div>");

                // Trabajadores más activos
                html.append("<div class='section'><h2>Trabajadores más activos (TOP 5)</h2>");
                html.append("<table><tr><th>Trabajador</th><th>Total reservas</th></tr>");
                rs = st.executeQuery(
                        "SELECT CONCAT(T.nombreTRA,' ',T.apellidoTRA) AS trabajador, COUNT(R.id_reser) AS total " +
                                "FROM TRABAJADOR T " +
                                "LEFT JOIN RESERVA R ON T.idTRA = R.idTRA " +
                                "GROUP BY T.idTRA " +
                                "ORDER BY total DESC " +
                                "LIMIT 5"
                );
                while (rs.next()) {
                    html.append("<tr><td>").append(rs.getString("trabajador")).append("</td>");
                    html.append("<td>").append(rs.getInt("total")).append("</td></tr>");
                }
                html.append("</table></div>");

                // Planes activos
                html.append("<div class='section'><h2>Planes activos</h2>");
                html.append("<table><tr><th>Cliente</th><th>Plan</th><th>Inicio</th><th>Fin</th></tr>");
                rs = st.executeQuery(
                        "SELECT CONCAT(C.nombreCLI,' ',C.apellidoCLI) AS cliente, S.tipo_sus, S.fecha_ini_sus, S.fecha_fin_sus " +
                                "FROM SUSCRIPCION S " +
                                "JOIN CLIENTE C ON S.idCLI = C.idCLI " +
                                "WHERE S.fecha_fin_sus >= NOW() " +
                                "ORDER BY S.fecha_ini_sus DESC"
                );
                while (rs.next()) {
                    html.append("<tr><td>").append(rs.getString("cliente")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_sus")).append("</td>");
                    html.append("<td>").append(rs.getDate("fecha_ini_sus")).append("</td>");
                    html.append("<td>").append(rs.getDate("fecha_fin_sus")).append("</td></tr>");
                }
                html.append("</table></div>");

                // Servicios sin reservas
                html.append("<div class='section'><h2>Servicios sin reservas</h2><ul>");
                rs = st.executeQuery(
                        "SELECT tipo_serv FROM SERVICIO WHERE id_serv NOT IN (SELECT id_serv FROM CONTIENE_2)"
                );
                while (rs.next()) {
                    html.append("<li>").append(rs.getString("tipo_serv")).append("</li>");
                }
                html.append("</ul></div>");

                // Clientes sin reservas
                html.append("<div class='section'><h2>Clientes sin reservas</h2><ul>");
                rs = st.executeQuery(
                        "SELECT CONCAT(nombreCLI,' ',apellidoCLI) AS cliente FROM CLIENTE WHERE idCLI NOT IN (SELECT idCLI FROM RESERVA)"
                );
                while (rs.next()) {
                    html.append("<li>").append(rs.getString("cliente")).append("</li>");
                }
                html.append("</ul></div>");

                // Trabajadores sin reservas
                html.append("<div class='section'><h2>Trabajadores sin reservas</h2><ul>");
                rs = st.executeQuery(
                        "SELECT CONCAT(T.nombreTRA,' ',T.apellidoTRA) AS trabajador " +
                                "FROM TRABAJADOR T " +
                                "LEFT JOIN RESERVA R ON T.idTRA = R.idTRA " +
                                "WHERE R.idTRA IS NULL"
                );
                while (rs.next()) {
                    html.append("<li>").append(rs.getString("trabajador")).append("</li>");
                }
                html.append("</ul></div>");

            } catch (SQLException e) {
                html.append("<p>Error al cargar datos: ").append(e.getMessage()).append("</p>");
                e.printStackTrace();
            }

            html.append("<br><a href='/index.html'><button class='btn btn-principal'>Volver al inicio</button></a>");
            html.append("</div></body></html>");
            res.type("text/html");
            return html.toString();
        });

        // --------------------
        // Vista de datos Operario
        // --------------------
        get("/misTrabajos", (req, res) -> {
            String usuarioTRA = req.queryParams("usuarioTRA");
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Trabajos Asignados</title>");
            html.append("<link rel='stylesheet' type='text/css' href='/panel.css'>");
            html.append("</head><body>");
            html.append("<h1>Trabajos asignados para ").append(usuarioTRA).append("</h1>");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

                int idTRA = obtenerTrabajadorIdPorUsuario(usuarioTRA);

                String sqlInfo = "SELECT nombreTRA, apellidoTRA, correoTRA, telefonoTRA, tipoTRA FROM TRABAJADOR WHERE idTRA = ?";
                PreparedStatement psInfo = conn.prepareStatement(sqlInfo);
                psInfo.setInt(1, idTRA);
                ResultSet rsInfo = psInfo.executeQuery();
                if (rsInfo.next()) {
                    html.append("<p>Nombre: ").append(rsInfo.getString("nombreTRA")).append(" ").append(rsInfo.getString("apellidoTRA")).append("</p>");
                    html.append("<p>Email: ").append(rsInfo.getString("correoTRA")).append("</p>");
                    html.append("<p>Teléfono: ").append(rsInfo.getString("telefonoTRA")).append("</p>");
                    html.append("<p>Tipo: ").append(rsInfo.getString("tipoTRA")).append("</p>");
                }

                html.append("<h2>Reservas asignadas</h2>");
                html.append("<table><tr><th>Cliente</th><th>Servicio</th><th>Fecha Reserva</th></tr>");
                String sqlRes = "SELECT C.nombreCLI, C.apellidoCLI, S.tipo_serv, R.fecha_reser " +
                        "FROM RESERVA R " +
                        "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                        "JOIN CONTIENE_2 CT ON R.id_reser = CT.id_reser " +
                        "JOIN SERVICIO S ON CT.id_serv = S.id_serv " +
                        "WHERE R.idTRA = ? " +
                        "ORDER BY fecha_reser DESC";
                PreparedStatement psRes = conn.prepareStatement(sqlRes);
                psRes.setInt(1, idTRA);
                ResultSet rsRes = psRes.executeQuery();
                while (rsRes.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rsRes.getString(1)).append(" ").append(rsRes.getString(2)).append("</td>");
                    html.append("<td>").append(rsRes.getString(3)).append("</td>");
                    html.append("<td>").append(rsRes.getTimestamp(4)).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");

            } catch (SQLException e) {
                html.append("<p>Error al obtener datos: ").append(e.getMessage()).append("</p>");
                e.printStackTrace();
            }

            html.append("</body></html>");
            res.type("text/html");
            return html.toString();
        });

    }

    // --------------------
    // Métodos auxiliares
    // --------------------
    private static boolean validarCliente(String usuario, String contrasena) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT * FROM CLIENTE WHERE usuarioCLI = ? AND contrasenaCLI = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, usuario);
            ps.setString(2, contrasena);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String validarTrabajadorTipo(String usuario, String contrasena) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT tipoTRA FROM TRABAJADOR WHERE usuarioTRA = ? AND contrasenaTRA = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, usuario);
            ps.setString(2, contrasena);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("tipoTRA");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int obtenerClienteIdPorNombre(String nombre) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT idCLI FROM CLIENTE WHERE usuarioCLI = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, nombre);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static int obtenerServicioIdPorNombre(String nombre, Connection conn) throws SQLException {
    String sql = "SELECT id_serv FROM SERVICIO WHERE TRIM(LOWER(tipo_serv)) = LOWER(?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, nombre.trim());
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
    }
    return -1;
}


    private static int obtenerTrabajadorIdPorUsuario(String usuario) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT idTRA FROM TRABAJADOR WHERE usuarioTRA = ?";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, usuario);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
private static Integer obtenerTrabajadorAsignado(Connection conn, int idServicio) throws SQLException {
    // Depuración: mostrar idServicio
    System.out.println("Buscando trabajador para idServicio: " + idServicio);

    if (idServicio <= 0) return null; // evita llamadas inválidas

    String sql = "SELECT idTRA FROM TRABAJA WHERE id_serv = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, idServicio);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int idTRA = rs.getInt("idTRA");
                System.out.println("Trabajador asignado encontrado: " + idTRA);
                return idTRA;
            } else {
                System.out.println("No se encontró trabajador para este servicio!");
            }
        }
    }
    return null;
}
}