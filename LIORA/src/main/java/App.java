package app;

import static spark.Spark.*;
import java.sql.*;

public class App {

    private static final String DB_URL = "jdbc:mysql://mysql-8001.dinaserver.com/Liora?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "Liora";
    private static final String DB_PASS = "Liora@2026";

    public static void main(String[] args) {
        port(4567);

        // Sirve los HTML desde /public
        staticFiles.location("/public");

        // --- PÁGINA INICIAL ---
        get("/", (req, res) -> {
            res.redirect("/index.html");
            return null;
        });

        // --- REGISTRO NUEVO USUARIO ---
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
                String sql = "INSERT INTO CLIENTE(nombreCLI, apellidoCLI, fecha_nacCLI, dniCLI, telefonoCLI, correoCLI, usuarioCLI, contrasenaCLI) "
                           + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
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

        // --- LOGIN CLIENTE ---
        post("/loginCLI", (req, res) -> {
            String usuario = req.queryParams("usuarioCLI");
            String contrasena = req.queryParams("contrasenaCLI");

            if (validarCliente(usuario, contrasena)) {
                res.redirect("/formu.html");
            } else {
                return "Usuario o contraseña incorrectos (CLI) usuario:" + usuario + " contraseña:" + contrasena;
            }
            return null;
        });

        // --- LOGIN TRABAJADOR ---
        post("/loginTRA", (req, res) -> {
            String usuario = req.queryParams("usuarioTRA");
            String contrasena = req.queryParams("contrasenaTRA");

            if (validarTRABAJADOR(usuario, contrasena)) {
                res.redirect("/datos");
            } else {
                return "Usuario o contraseña incorrectos (TRA) usuario:" + usuario + " contraseña:" + contrasena;
            }
            return null;
        });

        // --- RESERVA PACK ---
        post("/reservaPack", (req, res) -> {
            String nombre = req.queryParams("nombreCLI");
            String[] servicios = req.queryParamsValues("servicios_pack[]");

            int usuarioId = obtenerClienteIdPorNombre(nombre);

            if (usuarioId > 0 && servicios != null) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    String sql = "INSERT INTO RESERVA(idCLI, id_serv) VALUES (?, ?)";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    for (String s : servicios) {
                        int idServ = obtenerServicioIdPorNombre(s, conn);
                        if (idServ > 0) {
                            ps.setInt(1, usuarioId);
                            ps.setInt(2, idServ);
                            ps.executeUpdate();
                        }
                    }
                } catch (SQLException e) { e.printStackTrace(); }
            }

            res.redirect("/formu_pack.html");
            return null;
        });

        // --- RESERVA PLAN ---
        post("/reservaPlan", (req, res) -> {
            String nombre = req.queryParams("nombreCLI");
            String plan = req.queryParams("plan_mensual");

            int usuarioId = obtenerClienteIdPorNombre(nombre);

            if (usuarioId > 0 && plan != null) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                    String sql = "INSERT INTO SUSCRIPCION(idCLI, tipo_sus, fecha_ini_sus, fecha_fin_sus) VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL 1 MONTH))";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setInt(1, usuarioId);
                    ps.setString(2, plan);
                    ps.executeUpdate();
                } catch (SQLException e) { e.printStackTrace(); }
            }

            res.redirect("/formu_plan.html");
            return null;
        });

        // ==============================
        // --- NUEVA RUTA PARA VER DATOS ---
        // ==============================
        get("/datos", (req, res) -> {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Datos SPA</title></head><body>");
            html.append("<h1>Listado de Reservas</h1>");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                // --- RESERVAS ---
                html.append("<h2>Reservas</h2>");
                html.append("<table border='1'><tr><th>Usuario</th><th>Nombre</th><th>Apellido</th><th>Servicios</th></tr>");
                String sqlRes = "SELECT C.usuarioCLI, C.nombreCLI, C.apellidoCLI, GROUP_CONCAT(S.tipo_serv SEPARATOR ', ') AS servicios " +
                                "FROM RESERVA R " +
                                "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                                "JOIN CONTIENE_2 CT ON R.id_reser = CT.id_reser " +
                                "JOIN SERVICIO S ON CT.id_serv = S.id_serv " +
                                "GROUP BY R.id_reser";
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sqlRes);
                while (rs.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rs.getString("usuarioCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("nombreCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("apellidoCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("servicios")).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");

                // --- PACKS ---
                html.append("<h2>Packs</h2>");
                html.append("<table border='1'><tr><th>Usuario</th><th>Nombre</th><th>Apellido</th><th>Pack</th></tr>");
                String sqlPack = "SELECT C.usuarioCLI, C.nombreCLI, C.apellidoCLI, P.tipo_pack " +
                                 "FROM RESERVA R " +
                                 "JOIN CLIENTE C ON R.idCLI = C.idCLI " +
                                 "JOIN CONTIENE CP ON R.id_reser = CP.id_reser " +
                                 "JOIN PACK P ON CP.id_pack = P.id_pack " +
                                 "GROUP BY R.id_reser, P.tipo_pack";
                rs = st.executeQuery(sqlPack);
                while (rs.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rs.getString("usuarioCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("nombreCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("apellidoCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_pack")).append("</td>");
                    html.append("</tr>");
                }
                html.append("</table>");

                // --- PLANES/SUSCRIPCIONES ---
                html.append("<h2>Planes Mensuales</h2>");
                html.append("<table border='1'><tr><th>Usuario</th><th>Nombre</th><th>Apellido</th><th>Plan</th><th>Inicio</th><th>Fin</th></tr>");
                String sqlPlan = "SELECT C.usuarioCLI, C.nombreCLI, C.apellidoCLI, S.tipo_sus, S.fecha_ini_sus, S.fecha_fin_sus " +
                                 "FROM SUSCRIPCION S " +
                                 "JOIN CLIENTE C ON S.idCLI = C.idCLI " +
                                 "ORDER BY S.fecha_ini_sus DESC";
                rs = st.executeQuery(sqlPlan);
                while (rs.next()) {
                    html.append("<tr>");
                    html.append("<td>").append(rs.getString("usuarioCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("nombreCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("apellidoCLI")).append("</td>");
                    html.append("<td>").append(rs.getString("tipo_sus")).append("</td>");
                    html.append("<td>").append(rs.getDate("fecha_ini_sus")).append("</td>");
                    html.append("<td>").append(rs.getDate("fecha_fin_sus")).append("</td>");
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

    // ==============================
    // --- MÉTODOS EXISTENTES ---
    // ==============================

    private static boolean validarCliente(String usuario, String contrasena) {
        String sql = "SELECT * FROM CLIENTE WHERE usuarioCLI = ? AND contrasenaCLI = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, contrasena);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean validarTRABAJADOR(String usuario, String contrasena) {
        String sql = "SELECT * FROM TRABAJADOR WHERE usuarioTRA = ? AND contrasenaTRA = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setString(2, contrasena);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static int obtenerClienteIdPorNombre(String nombre) {
        String sql = "SELECT idCLI FROM CLIENTE WHERE nombreCLI= ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("idCLI");
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    private static int obtenerServicioIdPorNombre(String nombreServ, Connection conn) {
        String sql = "SELECT id_serv FROM SERVICIO WHERE tipo_serv = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombreServ);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id_serv");
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }
}
