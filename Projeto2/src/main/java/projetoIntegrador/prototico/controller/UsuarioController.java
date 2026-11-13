package projetoIntegrador.prototico.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import projetoIntegrador.prototico.model.TipoUsuario;
import projetoIntegrador.prototico.model.Usuario;
import projetoIntegrador.prototico.repository.UsuarioRepository;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<Usuario> listar() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Usuario> buscarPorId(@PathVariable Long id) {
        Optional<Usuario> usuarioBuscado = repository.findById(id);
        return usuarioBuscado.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario criar(@RequestBody @Valid Usuario usuario) {
        if (repository.existsByEmail(usuario.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail já cadastrado");
        }
        if (usuario.getTipoUsuario() == null) {
            usuario.setTipoUsuario(TipoUsuario.CLIENTE);
        } else if (usuario.getTipoUsuario() == TipoUsuario.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não é permitido cadastrar um ADMIN diretamente.");
        }

        usuario.setSenha(passwordEncoder.encode(usuario.getSenha()));
        return repository.save(usuario);
    }

    @PatchMapping("/tornar-vendedor")
    public ResponseEntity<Usuario> tornarVendedor(
            @AuthenticationPrincipal Usuario usuarioLogado,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (usuarioLogado.getTipoUsuario() == TipoUsuario.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Administradores já possuem acesso de vendedor.");
        }
        if (usuarioLogado.getTipoUsuario() == TipoUsuario.VENDEDOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este usuário já é um vendedor.");
        }

        usuarioLogado.setTipoUsuario(TipoUsuario.VENDEDOR);
        Usuario usuarioAtualizado = repository.save(usuarioLogado);

        // Atualiza as permissões na sessão ativa imediatamente
        var novaAuth = new UsernamePasswordAuthenticationToken(
                usuarioAtualizado, null, usuarioAtualizado.getAuthorities());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(novaAuth);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        return ResponseEntity.ok(usuarioAtualizado);
    }

    @PutMapping("/{id}")
    public Usuario atualizar(
            @PathVariable Long id,
            @RequestBody @Valid Usuario usuario,
            @AuthenticationPrincipal Usuario usuarioLogado) {

        Usuario usuarioExistente = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));

        if (!usuarioExistente.getId().equals(usuarioLogado.getId()) && usuarioLogado.getTipoUsuario() != TipoUsuario.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem permissão para alterar este usuário.");
        }

        usuarioExistente.setNome(usuario.getNome());
        usuarioExistente.setTelefone(usuario.getTelefone());
        usuarioExistente.setEmail(usuario.getEmail());
        usuarioExistente.setSenha(passwordEncoder.encode(usuario.getSenha()));
        usuarioExistente.setEndereco(usuario.getEndereco());

        if (usuarioLogado.getTipoUsuario() == TipoUsuario.ADMIN && usuario.getTipoUsuario() != null) {
            usuarioExistente.setTipoUsuario(usuario.getTipoUsuario());
        }

        return repository.save(usuarioExistente);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
        }
        repository.deleteById(id);
    }
}