package com.bigpig.api.controller;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.nio.charset.StandardCharsets;

import com.bigpig.api.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.bigpig.api.service.KeyService;
import com.bigpig.api.service.UserService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/users")
public class MainController {

    @Autowired
    private UserService userService;

    @Autowired
    private KeyService keyService;

    @PostMapping("/login")
    public ResponseEntity<?> login(HttpSession session, @RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        password = hashPassword(password);  // Hash della password

        User user = userService.findByUsernamePassword(username, password);
        if (user != null) {
            session.setAttribute("user", user);  // Memorizza l'utente nella sessione
            return ResponseEntity.ok().body("{\"success\": true}");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"success\": false, \"message\": \"Wrong credentials\"}");
        }
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(HttpSession session, @RequestBody SingInRequest singinRequest) {
        String name = singinRequest.getName();
        String surname = singinRequest.getSurname();
        String username = singinRequest.getUsername();
        String password = singinRequest.getPassword();
        User existingUser = userService.findByUsername(username);
        // Verifica che i dati non siano nulli o vuoti
        if (name == null || name.isEmpty() ||
                surname == null || surname.isEmpty() ||
                username == null || username.isEmpty() ||
                password == null || password.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"success\": false, \"message\": \"All fields must be filled\"}");
        }
        if (existingUser != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("{\"success\": false, \"message\": \"Username already exists\"}");
        } else {
            User newUser = new User();
            newUser.setName(name);
            newUser.setSurname(surname);
            newUser.setUsername(username);
            newUser.setPassword(hashPassword(password));
            userService.save(newUser);
            return ResponseEntity.ok().body("{\"success\": true}");
        }
    }


    @PostMapping("/addKey")
    public ResponseEntity<?> addKey(RedirectAttributes redirectAttributes, Model model, HttpSession session, @RequestBody AddKey addKey) {
        String key = addKey.getKey();
        String username = addKey.getUser();
        Key newKey = new Key();
        newKey.setPublicK(key);
        newKey.setValidazioni(true);
        newKey.setUser(userService.findByUsername(username));
        keyService.save(newKey);
        return ResponseEntity.ok().body("{\"message\": \"Chiave Inserita\"}");
    }

    @PutMapping("/disableKey")
    public ResponseEntity<?> disableKey(@RequestParam("key") String key) {
        Key existingKey = keyService.findById(key);
        if (existingKey != null) {
            existingKey.setValidazioni(false);
            keyService.save(existingKey);
            return ResponseEntity.ok().body("{\"message\": \"Chiave Disabilitata\"}");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"message\": \"Chiave non trovata\"}");
        }
    }

    @GetMapping("/getKeys")
    public ResponseEntity<?> getKeys(HttpSession session) {
        //User user = (User) session.getAttribute("user");
       // if (user != null) {
            List<Key> keys = keyService.findAll();
            return ResponseEntity.ok().body(keys);
      /*  } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"message\": \"User not logged in\"}");
        }*/
    }



    @GetMapping("/getKeysByUsername")
    public ResponseEntity<?> getKeysByUsername(@RequestParam("username") String username, Model model) {
        User user = userService.findByUsername(username);
        if (user != null) {
            List<Key> keys = keyService.findByUser(user);
            model.addAttribute("keys", keys);
            return ResponseEntity.ok().body(keys);
        } else {
            model.addAttribute("msg", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"success\": false, \"message\": \"User not found\"}");
        }
    }

    @GetMapping("/generateKey")
    public ResponseEntity<?> getGeneratedKeys(HttpSession session, @RequestParam int keySize, @RequestParam String username) {
        try {
            // Verifica se la dimensione della chiave è valida
            if (keySize != 2048 && keySize != 3072 && keySize != 4096) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"success\": false, \"message\": \"Invalid key size\"}");
            }

            // Generazione della coppia di chiavi RSA
            KeyPair keyPair = RSAKeyUtility.generateKeyPair(keySize);

            // Codifica in Base64 le chiavi pubblica e privata
            String publicKey = RSAKeyUtility.toBase64(keyPair.getPublic().getEncoded());
            String privateKey = RSAKeyUtility.toBase64(keyPair.getPrivate().getEncoded());

            // Verifica se le chiavi sono state generate correttamente
            if (publicKey == null || privateKey == null) {
                if (username != null) {
                    // Se il nome utente è valido, salva la chiave
                    Key newKey = new Key();
                    newKey.setPublicK(publicKey);
                    newKey.setValidazioni(true);
                    newKey.setUser(userService.findByUsername(username));
                    keyService.save(newKey);

                    // Restituisci una risposta di successo
                    return ResponseEntity.ok().body("{\"success\": true, \"message\": \"Key generated successfully\"}");
                } else {
                    // Se l'username non è valido, restituisci un errore 404
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"success\": false, \"message\": \"User not found\"}");
                }
            } else {
                // Se la generazione delle chiavi è fallita, restituisci un errore 500
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"success\": false, \"message\": \"Key Generation Failed\"}");
            }

        } catch (NoSuchAlgorithmException e) {
            // Log dell'errore in caso di eccezione
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"success\": false, \"message\": \"Error during key generation: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            // Gestione di eventuali altre eccezioni
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"success\": false, \"message\": \"Unexpected error occurred: " + e.getMessage() + "\"}");
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
//ciao