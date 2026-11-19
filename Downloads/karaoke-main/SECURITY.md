# Security Documentation - GLAM 2.0 Karaoke

## Overview
This document describes security improvements implemented in the karaoke application.

## Fixed Vulnerabilities

### 1. Hardcoded Admin Password (CRITICAL)
**Before:** `const ADMIN_PASSWORD = "DenisGlam";` in frontend JavaScript - visible to all users.
**After:** Password configured via environment variable `ADMIN_PASSWORD`. Backend validates via `/api/admin/login` endpoint. Frontend sends credentials to backend, never stores password.

### 2. Missing Authentication/Authorization (HIGH)
**Before:** All API endpoints publicly accessible without any auth.
**After:** 
- Admin endpoints protected by password check
- Spring Security configured with security headers
- Rate limiting on all endpoints

### 3. Cross-Site Scripting (XSS) Risk (HIGH)
**Before:** User inputs directly interpolated into HTML via `innerHTML` without escaping.
**After:** 
- All user-generated content escaped via `escapeHtml()` function
- DOM manipulation uses `textContent` instead of `innerHTML` where possible
- CSP headers restrict script sources

### 4. Open Redirect (MEDIUM)
**Before:** `window.open(songUrl)` without validation - could redirect to malicious sites.
**After:** `isSafeUrl()` validates URL protocol (only http/https allowed) before opening.

### 5. Missing Security Headers (MEDIUM)
**Before:** No security headers.
**After:** Comprehensive headers via Spring Security:
- Content-Security-Policy
- Strict-Transport-Security (HSTS)
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- Referrer-Policy: strict-origin-when-cross-origin
- Permissions-Policy
- Cross-Origin-Opener-Policy
- Cross-Origin-Resource-Policy

### 6. No Rate Limiting (MEDIUM)
**Before:** Unlimited requests - vulnerable to DoS.
**After:** In-memory rate limiter by IP:
- Search: 30 req/min
- Queue operations: 20 req/min
- General: 60 req/min
- Returns 429 with Retry-After header

### 7. CSV Injection / URL Encoding (LOW)
**Before:** URL building with simple `replace(" ", "+")` - breaks on special chars.
**After:** Proper `URLEncoder.encode()` with UTF-8.

### 8. Information Exposure (LOW)
**Before:** Detailed error messages in responses.
**After:** Generic error messages, detailed logs server-side only.

### 9. Docker Security (MEDIUM)
**Before:** Root user, no health check, Java 25 (unstable).
**After:**
- Multi-stage build
- Non-root user (appuser)
- Java 21 LTS
- Health check endpoint
- JVM container-aware settings
- .dockerignore

## Configuration

### Required Environment Variables
```bash
ADMIN_PASSWORD=your-strong-random-password  # Required for admin access
```

### Optional
```bash
JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

## Deployment Checklist

- [ ] Set `ADMIN_PASSWORD` as secret in Render/environment (NOT in code!)
- [ ] Use HTTPS in production (Render provides this automatically)
- [ ] Configure CORS origins to specific domain (not `*`) in production
- [ ] Enable actuator health endpoint monitoring
- [ ] Set up log aggregation
- [ ] Consider adding proper authentication (OAuth2/OIDC) for admin panel
- [ ] Use BCrypt/Argon2 for password hashing (currently plaintext comparison)
- [ ] Add persistent storage for queue (currently in-memory)
- [ ] Implement CSRF tokens if adding forms

## API Endpoints Security

| Endpoint | Auth | Rate Limit | Notes |
|----------|------|------------|-------|
| GET /api/songs | None | 30/min | Search with query params |
| GET /api/queue | None | 60/min | Public queue view |
| POST /api/queue | None | 20/min | Add to queue |
| PUT /api/queue/{id}/status | Admin* | 20/min | Requires admin login |
| DELETE /api/queue/{id} | Admin* | 20/min | Requires admin login |
| POST /api/admin/login | None | 10/min | Password check |
| GET /api/admin/stats | Admin* | 10/min | Requires admin login |
| GET /actuator/health | None | - | Health check |

*Admin check currently via frontend session flag - should be strengthened with proper session/JWT.

## Frontend Security Features

- Input sanitization (maxlength, character filtering)
- Debounced search (300ms) to reduce server load
- Toast notifications instead of alert()
- XSS-safe DOM manipulation
- URL validation before window.open()
- Admin mode requires backend verification

## Future Improvements

1. **Password Hashing**: Use BCrypt for admin password storage
2. **Session Management**: JWT tokens or server-side sessions for admin
3. **Persistent Storage**: Database for queue and songs (survive restarts)
4. **Audit Logging**: Log all admin actions
5. **WAF**: Cloudflare or similar for additional protection
6. **Dependency Scanning**: Regular `mvn dependency-check:check`
7. **Penetration Testing**: Annual security assessment

## Incident Response

If compromise suspected:
1. Rotate `ADMIN_PASSWORD` immediately
2. Check logs for unusual admin logins
3. Review queue for unauthorized entries
4. Rebuild and redeploy from clean source