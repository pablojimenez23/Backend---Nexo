package com.nexo.msqueues.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexo.msqueues.config.SecurityConfig;
import com.nexo.msqueues.dto.QueueCreateRequest;
import com.nexo.msqueues.dto.QueueResponse;
import com.nexo.msqueues.exception.QueueAlreadyExistsException;
import com.nexo.msqueues.exception.QueueNotFoundException;
import com.nexo.msqueues.service.SqsAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Seguridad (solo ADMIN), validación y códigos HTTP del controlador de colas. */
@WebMvcTest(QueueController.class)
@Import(SecurityConfig.class)
class QueueControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SqsAdminService admin;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final QueueResponse RESPUESTA = new QueueResponse("nexo-prueba", "url", "arn", 0, 0, 30, 345600,
            "arn-dlq", 3);

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor cliente() {
        return jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    // ---------------------------------------------------------- seguridad

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/queues")).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioNoAdminDevuelve403() throws Exception {
        mvc.perform(get("/queues").with(cliente())).andExpect(status().isForbidden());
        mvc.perform(post("/queues").with(cliente()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"nexo-prueba\"}")).andExpect(status().isForbidden());
        verify(admin, never()).crear(any());
    }

    // ------------------------------------------------------------ crear

    @Test
    void crearValidaDevuelve201ConLocation() throws Exception {
        when(admin.crear(any(QueueCreateRequest.class))).thenReturn(RESPUESTA);
        mvc.perform(post("/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nexo-prueba\",\"maxReceiveCount\":3}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/messaging/queues/nexo-prueba"))
                .andExpect(jsonPath("$.name").value("nexo-prueba"));
    }

    @Test
    void crearConNombreInvalidoDevuelve400SinLlamarAlServicio() throws Exception {
        mvc.perform(post("/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MiCola\"}"))
                .andExpect(status().isBadRequest());
        verify(admin, never()).crear(any());
    }

    @Test
    void crearConMaxReceiveCountFueraDeRangoDevuelve400() throws Exception {
        mvc.perform(post("/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nexo-prueba\",\"maxReceiveCount\":50}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearConJsonMalformadoDevuelve400() throws Exception {
        mvc.perform(post("/queues").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearColaExistenteDevuelve409() throws Exception {
        when(admin.crear(any(QueueCreateRequest.class))).thenThrow(new QueueAlreadyExistsException("nexo-prueba"));
        mvc.perform(post("/queues").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"nexo-prueba\"}"))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------- consultar

    @Test
    void obtenerExistenteDevuelve200() throws Exception {
        when(admin.obtener("nexo-prueba")).thenReturn(RESPUESTA);
        mvc.perform(get("/queues/nexo-prueba").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxReceiveCount").value(3));
    }

    @Test
    void obtenerInexistenteDevuelve404() throws Exception {
        when(admin.obtener("nexo-fantasma")).thenThrow(new QueueNotFoundException("nexo-fantasma"));
        mvc.perform(get("/queues/nexo-fantasma").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void nombreEnLaRutaInvalidoDevuelve400() throws Exception {
        mvc.perform(get("/queues/MAYUSCULAS").with(admin())).andExpect(status().isBadRequest());
        verify(admin, never()).obtener(any());
    }

    // ------------------------------------------------ modificar / eliminar

    @Test
    void actualizarDevuelve200() throws Exception {
        when(admin.actualizar(eq("nexo-prueba"), any())).thenReturn(RESPUESTA);
        mvc.perform(put("/queues/nexo-prueba").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibilityTimeoutSeconds\":60}"))
                .andExpect(status().isOk());
    }

    @Test
    void actualizarConValorInvalidoDevuelve400() throws Exception {
        mvc.perform(put("/queues/nexo-prueba").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionSeconds\":5}"))
                .andExpect(status().isBadRequest());
        verify(admin, never()).actualizar(any(), any());
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        mvc.perform(delete("/queues/nexo-prueba").with(admin())).andExpect(status().isNoContent());
        verify(admin).eliminar(eq("nexo-prueba"), anyBoolean());
    }

    @Test
    void vaciarDevuelve204() throws Exception {
        mvc.perform(post("/queues/nexo-prueba/purge").with(admin())).andExpect(status().isNoContent());
        verify(admin).vaciar("nexo-prueba");
    }

    // ------------------------------------------------------- mensajes

    @Test
    void enviarMensajeVacioDevuelve400() throws Exception {
        mvc.perform(post("/queues/nexo-prueba/messages").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(admin, never()).enviarMensaje(any(), any());
    }

    @Test
    void enviarMensajeValidoDevuelve201() throws Exception {
        when(admin.enviarMensaje("nexo-prueba", "hola")).thenReturn("m-1");
        mvc.perform(post("/queues/nexo-prueba/messages").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hola\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value("m-1"));
    }

    @Test
    void verMensajesConMaxFueraDeRangoDevuelve400() throws Exception {
        mvc.perform(get("/queues/nexo-prueba/messages?max=50").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void reenviarDlqDevuelve202() throws Exception {
        when(admin.reenviarDesdeDlq("nexo-prueba-dlq")).thenReturn("tarea-1");
        mvc.perform(post("/queues/nexo-prueba-dlq/redrive").with(admin())).andExpect(status().isAccepted());
    }
}