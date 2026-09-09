# Build stage
FROM node:18-alpine AS builder

WORKDIR /app

# Copy manifests first so the dependency layer caches across source changes.
COPY package.json package-lock.json ./

# `npm ci` installs exactly what the lockfile pins, unlike `npm install` which
# can silently resolve different transitive versions on every build.
RUN npm ci

# Copy source code
COPY . .

# Build the React application
RUN npm run build

# Serve stage
FROM nginx:alpine

# Copy built assets to Nginx
COPY --from=builder /app/build /usr/share/nginx/html

# Expose port
EXPOSE 80

# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
