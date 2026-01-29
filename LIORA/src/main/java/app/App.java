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
                    res.redirect("/datos");
                } else {
                    res.redirect("/misTrabajos?usuarioTRA=" + usuario);
                }
            } else {
                return "Usuario o contraseña incorrectos (TRA)";
            }
            return null;
        });

        // --------------------
        // Reserva de servicios/pack
        // --------------------
        post("/reservaPack", (req, res) -> {
            String nombre = req.queryParams("nombreCLI");
            String[] servicios = req.queryParamsValues("servicios_pack[]");
            String[] packs = req.queryParamsValues("packs[]");
            String fecha = req.queryParams("fecha_reserva");

            if (fecha != null) fecha = fecha.replace("T", " "); // datetime-local fix

            int usuarioId = obtenerClienteIdPorNombre(nombre);

            if (usuarioId > 0) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

                    // --- Reserva servicios individuales ---
                    if (servicios != null) {
                        for (String s : servicios) {
                            int idServ = obtenerServicioIdPorNombre(s, conn);
                            int idTRA = obtenerTrabajadorAsignado(conn, idServ); // Asignar directamente desde TRABAJA

                            String sqlReser = "INSERT INTO RESERVA(fecha_reser, idCLI, idTRA) VALUES (?, ?, ?)";
                            PreparedStatement psReser = conn.prepareStatement(sqlReser, Statement.RETURN_GENERATED_KEYS);
                            psReser.setTimestamp(1, java.sql.Timestamp.valueOf(fecha + ":00"));
                            psReser.setInt(2, usuarioId);
                            psReser.setInt(3, idTRA);
                            psReser.executeUpdate();

                            ResultSet rsKeys = psReser.getGeneratedKeys();
                            if (rsKeys.next()) {
                                int reservaId = rsKeys.getInt(1);
                                if (idServ > 0) {
                                    String sqlCont = "INSERT INTO CONTIENE_2(id_reser, id_serv) VALUES (?, ?)";
                                    PreparedStatement psCont = conn.prepareStatement(sqlCont);
                                    psCont.setInt(1, reservaId);
                                    psCont.setInt(2, idServ);
                                    psCont.executeUpdate();
                                }
                            }
                        }
                    }

                    // --- Reserva packs ---
                    if (packs != null) {
                        for (String p : packs) {
                            String sqlReser = "INSERT INTO RESERVA(fecha_reser, idCLI, idTRA) VALUES (?, ?, NULL)";
                            PreparedStatement psReser = conn.prepareStatement(sqlReser, Statement.RETURN_GENERATED_KEYS);
                            psReser.setTimestamp(1, java.sql.Timestamp.valueOf(fecha + ":00"));
                            psReser.setInt(2, usuarioId);
                            psReser.executeUpdate();

                            ResultSet rsKeys = psReser.getGeneratedKeys();
                            if (rsKeys.next()) {
                                int reservaId = rsKeys.getInt(1);
                                int idPack = obtenerPackIdPorNombre(p, conn);
                                if (idPack > 0) {
                                    String sqlCont = "INSERT INTO CONTIENE(id_reser, id_pack) VALUES (?, ?)";
                                    PreparedStatement psCont = conn.prepareStatement(sqlCont);
                                    psCont.setInt(1, reservaId);
                                    psCont.setInt(2, idPack);
                                    psCont.executeUpdate();
                                }
                            }
                        }
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    return "Error al registrar reserva: " + e.getMessage();
                }
            } else {
                return "Cliente no encontrado";
            }

            res.redirect("/reserva_confirmada.html");
            return null;
        });

        // --------------------
        // Reserva de plan mensual
        // --------------------
        post("/reservaPlan", (req, res) -> {
            String nombre = req.queryParams("nombreCLI");
            String plan = req.queryParams("plan_mensual");
            String fecha_ini = req.queryParams("fecha_ini");
            String[] serviciosExtra = req.queryParamsValues("servicios_plan[]");

            int usuarioId = obtenerClienteIdPorNombre(nombre);

            if (usuarioId > 0 && plan != null && fecha_ini != null) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    String sql = "INSERT INTO SUSCRIPCION(idCLI, tipo_sus, fecha_ini_sus, fecha_fin_sus) VALUES (?, ?, ?, DATE_ADD(?, INTERVAL 1 MONTH))";
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setInt(1, usuarioId);
                    ps.setString(2, plan);
                    ps.setDate(3, java.sql.Date.valueOf(fecha_ini));
                    ps.setDate(4, java.sql.Date.valueOf(fecha_ini));
                    ps.executeUpdate();

                    ResultSet rsKeys = ps.getGeneratedKeys();
                    int susId = -1;
                    if (rsKeys.next()) susId = rsKeys.getInt(1);

                    if (serviciosExtra != null && susId > 0) {
                        for (String s : serviciosExtra) {
                            int idServ = obtenerServicioIdPorNombre(s, conn);
                            if (idServ > 0) {
                                String sqlCont = "INSERT INTO CONTIENE_2(id_reser, id_serv) VALUES (?, ?)";
                                PreparedStatement psCont = conn.prepareStatement(sqlCont);
                                psCont.setInt(1, susId);
                                psCont.setInt(2, idServ);
                                psCont.executeUpdate();
                            }
                        }
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    return "Error al registrar plan: " + e.getMessage();
                }
            }

            res.redirect("/plan_confirmado.html");
            return null;
        });

        // --------------------
        // Vista de datos Admin
        // --------------------
        get("/datos", (req, res) -> {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Datos SPA - Admin</title></head><body>");
            html.append("<h1>Panel Admin - Todas las reservas, planes y trabajadores</h1>");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                // --- RESERVAS CON SERVICIOS ---
                html.append("<h2>Reservas por Cliente - Servicios</h2>");
                html.append("<table border='1'><tr><th>Usuario</th><th>Cliente</th><th>Servicio</th><th>Fecha Reserva</th><th>Trabajador</th></tr>");
                String sqlRes = "SELECT C.usuarioCLI, CONCAT(C.nombreCLI,' ',C.apellidoCLI) AS cliente, S.tipo_serv, R.fecha_reser, T.nombreTRA, T.apellidoTRA " +
                        "FROM RESERVA R " +
                        "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                        "JOIN CONTIENE_2 CT ON R.id_reser = CT.id_reser " +
                        "JOIN SERVICIO S ON CT.id_serv = S.id_serv " +
                        "LEFT JOIN TRABAJADOR T ON R.idTRA = T.idTRA " +
                        "ORDER BY R.fecha_reser DESC";
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sqlRes);
                while (rs.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rs.getString("usuarioCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("cliente")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_serv")).append("</td>");
                    html.append("<td>").append(rs.getTimestamp("fecha_reser")).append("</td>");
                    html.append("<td>").append(rs.getString("nombreTRA") != null ? rs.getString("nombreTRA")+" "+rs.getString("apellidoTRA") : "No asignado").append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");

                // --- RESERVAS CON PACKS ---
                html.append("<h2>Reservas por Cliente - Packs</h2>");
                html.append("<table border='1'><tr><th>Usuario</th><th>Cliente</th><th>Pack</th><th>Fecha Reserva</th><th>Trabajador</th></tr>");
                String sqlPack = "SELECT C.usuarioCLI, CONCAT(C.nombreCLI,' ',C.apellidoCLI) AS cliente, P.tipo_pack, R.fecha_reser, T.nombreTRA, T.apellidoTRA " +
                        "FROM RESERVA R " +
                        "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                        "JOIN CONTIENE CP ON R.id_reser = CP.id_reser " +
                        "JOIN PACK P ON CP.id_pack = P.id_pack " +
                        "LEFT JOIN TRABAJADOR T ON R.idTRA = T.idTRA " +
                        "ORDER BY R.fecha_reser DESC";
                rs = st.executeQuery(sqlPack);
                while (rs.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rs.getString("usuarioCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("cliente")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_pack")).append("</td>");
                    html.append("<td>").append(rs.getTimestamp("fecha_reser")).append("</td>");
                    html.append("<td>").append(rs.getString("nombreTRA") != null ? rs.getString("nombreTRA")+" "+rs.getString("apellidoTRA") : "No asignado").append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");

                // --- PLANES MENSUALES ---
                html.append("<h2>Planes Mensuales</h2>");
                html.append("<table border='1'><tr><th>Usuario</th><th>Cliente</th><th>Plan</th><th>Inicio</th><th>Fin</th></tr>");
                String sqlPlan = "SELECT C.usuarioCLI, CONCAT(C.nombreCLI,' ',C.apellidoCLI) AS cliente, S.tipo_sus, S.fecha_ini_sus, S.fecha_fin_sus " +
                        "FROM SUSCRIPCION S " +
                        "JOIN CLIENTE C ON S.idCLI = C.idCLI " +
                        "ORDER BY S.fecha_ini_sus DESC";
                rs = st.executeQuery(sqlPlan);
                while (rs.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rs.getString("usuarioCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("cliente")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_sus")).append("</td>");
                    html.append("<td>").append(rs.getDate("fecha_ini_sus")).append("</td>");
                    html.append("<td>").append(rs.getDate("fecha_fin_sus")).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");

                // --- TRABAJADORES Y SUS SERVICIOS ---
                html.append("<h2>Trabajadores y Servicios que realizan</h2>");
                html.append("<table border='1'><tr><th>Trabajador</th><th>Tipo</th><th>Servicio</th></tr>");
                String sqlTrab = "SELECT T.nombreTRA, T.apellidoTRA, T.tipoTRA, S.tipo_serv " +
                        "FROM TRABAJA TR " +
                        "JOIN TRABAJADOR T ON TR.idTRA = T.idTRA " +
                        "JOIN SERVICIO S ON TR.id_serv = S.id_serv " +
                        "ORDER BY T.nombreTRA";
                rs = st.executeQuery(sqlTrab);
                while (rs.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rs.getString("nombreTRA")).append(" ").append(rs.getString("apellidoTRA")).append("</td>");
                    html.append("<td>").append(rs.getString("tipoTRA")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_serv")).append("</td>");
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

        // --------------------
        // Vista de datos Operario
        // --------------------
        get("/misTrabajos", (req, res) -> {
            String usuarioTRA = req.queryParams("usuarioTRA");
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Trabajos Asignados</title></head><body>");
            html.append("<h1>Trabajos asignados para ").append(usuarioTRA).append("</h1>");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

                int idTRA = obtenerTrabajadorIdPorUsuario(usuarioTRA);

                // Info del trabajador
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

                // Reservas asignadas
                html.append("<h2>Reservas asignadas</h2>");
                html.append("<table border='1'><tr><th>Cliente</th><th>Servicio/Pack</th><th>Fecha Reserva</th></tr>");
                String sqlRes = "SELECT C.nombreCLI, C.apellidoCLI, S.tipo_serv, R.fecha_reser " +
                        "FROM RESERVA R " +
                        "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                        "JOIN CONTIENE_2 CT ON R.id_reser = CT.id_reser " +
                        "JOIN SERVICIO S ON CT.id_serv = S.id_serv " +
                        "WHERE R.idTRA = ? " +
                        "UNION ALL " +
                        "SELECT C.nombreCLI, C.apellidoCLI, P.tipo_pack, R.fecha_reser " +
                        "FROM RESERVA R " +
                        "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                        "JOIN CONTIENE CP ON R.id_reser = CP.id_reser " +
                        "JOIN PACK P ON CP.id_pack = P.id_pack " +
                        "WHERE R.idTRA = ? " +
                        "ORDER BY fecha_reser DESC";
                PreparedStatement psRes = conn.prepareStatement(sqlRes);
                psRes.setInt(1, idTRA);
                psRes.setInt(2, idTRA);
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
        String sql = "SELECT id_serv FROM SERVICIO WHERE tipo_serv = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, nombre);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt(1);
        return -1;
    }

    private static int obtenerPackIdPorNombre(String nombre, Connection conn) throws SQLException {
        String sql = "SELECT id_pack FROM PACK WHERE tipo_pack = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, nombre);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt(1);
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

    // --------------------
    // NUEVO: Obtener trabajador asignado desde TRABAJA
    // --------------------
    private static int obtenerTrabajadorAsignado(Connection conn, int idServicio) throws SQLException {
        String sql = "SELECT idTRA FROM TRABAJA WHERE id_serv = ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, idServicio);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) return rs.getInt("idTRA");
        return 0; // Por si acaso
    }
}
