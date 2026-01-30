# Dockerfile to build and run MASE Viewer
#
# docker build . -t mase-viewer
# docker run -p 3000:3000 -it mase-viewer

# Build the SvelteKit app
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files
COPY package.json package-lock.json* .npmrc* ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build the app
RUN npm run build

# Run the app
FROM node:20-alpine

WORKDIR /app

# Copy built app and dependencies
COPY --from=build /app/build ./build
COPY --from=build /app/package.json ./
COPY --from=build /app/node_modules ./node_modules

ENV PORT=3000
ENV HOST=0.0.0.0

EXPOSE 3000

CMD ["node", "build"]