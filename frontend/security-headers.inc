# Security headers for the pages and files served by nginx (finding F-1, Worksheet 05, Ex. 6).
# Included in every location that sets its own headers (nginx does not inherit add_header there).
# The API (/api) gets the same headers from Spring Security.
add_header X-Frame-Options "DENY" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
