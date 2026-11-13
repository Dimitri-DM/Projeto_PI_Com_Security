package projetoIntegrador.prototico.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String senha) {
}
/*Criei o LoginRequest pra bater com o AutenticadorController */