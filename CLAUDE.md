# BookLore AI Assistant Guide

This document provides comprehensive guidance for AI assistants working on the BookLore codebase. It covers architecture, conventions, workflows, and best practices to ensure effective and consistent contributions.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Codebase Structure](#codebase-structure)
3. [Tech Stack](#tech-stack)
4. [Development Setup](#development-setup)
5. [Code Organization & Patterns](#code-organization--patterns)
6. [Database Architecture](#database-architecture)
7. [API Structure](#api-structure)
8. [Build & Development Workflows](#build--development-workflows)
9. [Testing Guidelines](#testing-guidelines)
10. [Common Tasks](#common-tasks)
11. [Key Conventions](#key-conventions)
12. [Important Guidelines for AI Assistants](#important-guidelines-for-ai-assistants)

---

## Project Overview

**BookLore** is a self-hosted, full-stack web application for organizing and managing personal book collections. It provides an intuitive interface to browse, read, and track reading progress across PDFs, EPUBs, and comic books.

### Key Features

- Multi-user support with granular permissions
- Smart organization with custom shelves and magic shelves (rule-based)
- Kobo and KOReader device integration
- Auto-metadata fetching from multiple sources (Goodreads, Amazon, Google Books, Hardcover)
- BookDrop auto-import from watched folders
- OPDS feed support
- Built-in readers (PDF, EPUB, CBZ/CBR)
- Flexible authentication (local JWT + OIDC)
- Reading progress synchronization
- Email sharing, notes, and reviews

### Project Goals

- User-friendly library management
- Cross-device reading continuity
- Flexible deployment (Docker, Kubernetes)
- Extensible metadata and integration support

---

## Codebase Structure

```
booklore/
├── booklore-api/              # Java Spring Boot backend
│   ├── src/main/java/com/adityachandel/booklore/
│   │   ├── config/            # Spring configuration, security
│   │   ├── controller/        # REST API controllers
│   │   ├── model/             # Entities, DTOs, enums
│   │   ├── repository/        # Spring Data JPA repositories
│   │   ├── service/           # Business logic services
│   │   ├── mapper/            # MapStruct DTO ↔ Entity mappers
│   │   ├── task/              # Background tasks
│   │   ├── filter/            # Request/response filters
│   │   ├── exception/         # Exception handlers
│   │   └── util/              # Utility classes
│   ├── src/main/resources/
│   │   ├── application.yaml   # Application configuration
│   │   └── db/migration/      # Flyway database migrations
│   └── build.gradle           # Gradle build configuration
│
├── booklore-ui/               # Angular frontend
│   ├── src/app/
│   │   ├── core/              # App-wide services, guards, config
│   │   ├── features/          # Feature modules (book, settings, etc.)
│   │   ├── shared/            # Reusable components, services, models
│   │   └── app.routes.ts      # Routing configuration
│   ├── src/environments/      # Environment configurations
│   ├── package.json           # NPM dependencies
│   ├── angular.json           # Angular CLI configuration
│   └── tailwind.config.js     # TailwindCSS configuration
│
├── docs/                      # Documentation
├── docker/                    # Docker-related files
├── mariadb/                   # Database configuration
├── scripts/                   # Utility scripts
├── docker-compose.yml         # Production Docker compose
├── dev.docker-compose.yml     # Development Docker compose
├── Dockerfile                 # Container build definition
├── nginx.conf                 # Nginx configuration
└── start.sh                   # Startup script
```

---

## Tech Stack

### Backend (booklore-api)

| Component | Technology | Version |
|-----------|-----------|---------|
| **Language** | Java | 25 |
| **Framework** | Spring Boot | 3.5.1 |
| **Build Tool** | Gradle | 8.x |
| **Database** | MariaDB | 11.4.5+ |
| **ORM** | Hibernate | 7.1.3.Final |
| **Migrations** | Flyway | 11.13.2 |
| **Security** | Spring Security + JWT | jjwt 0.13.0 |
| **Mapping** | MapStruct | 1.6.3 |
| **Utilities** | Lombok | 1.18.42 |
| **PDF Processing** | Apache PDFBox | 3.0.5 |
| **EPUB Processing** | epub4j-core | 4.2.2 |
| **Comic Archives** | JUnrar | 7.5.5 |
| **Web Scraping** | JSoup | 1.21.2 |
| **API Docs** | SpringDoc OpenAPI | 2.8.9 |

**Key Libraries:**
- Spring Boot Starters: Web, Data JPA, Validation, WebSocket, Security, Mail, OAuth2 Client, Actuator
- Authentication: JWT, OAuth2/OIDC
- Real-time: WebSocket with STOMP
- Testing: JUnit, Mockito, AssertJ, MockWebServer

### Frontend (booklore-ui)

| Component | Technology | Version |
|-----------|-----------|---------|
| **Framework** | Angular (Standalone) | 20.3.5 |
| **Language** | TypeScript | 5.9.3 |
| **Build Tool** | Angular CLI | 20.3.6 |
| **Styling** | TailwindCSS | 3.4.17 |
| **UI Components** | PrimeNG | 20.0.1 |
| **State Management** | RxJS | 7.8.2 |
| **PDF Reader** | ngx-extended-pdf-viewer | 23.3.1 |
| **EPUB Reader** | epubjs | 0.3.93 |
| **Charts** | ng2-charts + Chart.js | 8.0.0 / 4.5.0 |
| **Authentication** | angular-oauth2-oidc | 20.0.0 |
| **WebSocket** | @stomp/rx-stomp | 2.0.1 |

**Key Libraries:**
- Virtual scrolling: @iharbeck/ngx-virtual-scroller
- Rich text: Quill 2.0.3
- Markdown: Showdown 2.1.0
- Animation: @tweenjs/tween.js
- Testing: Karma, Jasmine
- Linting: ESLint with angular-eslint

### Infrastructure

- **Database:** MariaDB 11.4.5 (via LinuxServer.io Docker image)
- **Reverse Proxy:** Nginx
- **Containerization:** Docker & Docker Compose
- **Orchestration:** Kubernetes (Helm charts available)

---

## Development Setup

### Prerequisites

- Java 25+ (or Java 21+ for compatibility)
- Node.js 18+
- MariaDB 11.4+
- Docker & Docker Compose (for containerized development)

### Quick Start with Docker

```bash
# Start all services
docker compose -f dev.docker-compose.yml up -d

# View logs
docker compose -f dev.docker-compose.yml logs -f booklore

# Stop services
docker compose -f dev.docker-compose.yml down
```

Access the application at `http://localhost:6060`

### Local Development (Without Docker)

#### 1. Database Setup

Start MariaDB and create database:

```sql
CREATE DATABASE booklore;
CREATE USER 'booklore'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON booklore.* TO 'booklore'@'localhost';
FLUSH PRIVILEGES;
```

#### 2. Backend Setup

Create `booklore-api/src/main/resources/application-dev.yml`:

```yaml
app:
  path-book: '/path/to/books'        # Book storage directory
  path-config: '/path/to/config'     # Config/thumbnails directory

spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://localhost:3306/booklore?createDatabaseIfNotExist=true
    username: booklore
    password: your_password
```

Run the backend:

```bash
cd booklore-api
./gradlew bootRun
```

Backend runs at `http://localhost:8080`
API docs at `http://localhost:8080/api/v1/swagger-ui.html`

#### 3. Frontend Setup

```bash
cd booklore-ui
npm install
npm start
# or
ng serve --open --watch --configuration development
```

Frontend runs at `http://localhost:4200`

---

## Code Organization & Patterns

### Backend Architecture

**Layered Architecture:**

```
Controller → Service → Repository → Database
    ↓          ↓
   DTO    ←  Mapper  →  Entity
```

**Layer Responsibilities:**

1. **Controllers** (`/controller/`)
   - Handle HTTP requests/responses
   - Input validation
   - Delegate to services
   - Return DTOs

2. **Services** (`/service/`)
   - Business logic
   - Transaction management
   - Coordinate multiple repositories
   - Domain-organized (book, library, user, metadata, etc.)

3. **Repositories** (`/repository/`)
   - Data access layer
   - Spring Data JPA interfaces
   - Custom queries with `@Query`

4. **Models** (`/model/`)
   - **Entities** (`/entity/`) - JPA entities with database mappings
   - **DTOs** (`/dto/`) - Data transfer objects for API
   - **Enums** (`/enums/`) - Type-safe enumerations

5. **Mappers** (`/mapper/`)
   - MapStruct interfaces
   - Automatic DTO ↔ Entity conversion
   - Custom mapping logic in `/mapper/custom/`

6. **Configuration** (`/config/`)
   - Spring beans
   - Security configuration
   - WebSocket configuration
   - Application properties binding

7. **Tasks** (`/task/`)
   - Background job implementations
   - Scheduled tasks
   - Task orchestration

**Key Service Domains:**

- `book/` - Book CRUD, file operations, recommendations
- `metadata/` - Fetching, parsing, writing metadata
- `library/` - Library management, scanning, organization
- `user/` - User management, authentication, permissions
- `kobo/` - Kobo device sync, KEPUB conversion
- `koreader/` - KOReader progress synchronization
- `opds/` - OPDS feed generation
- `bookdrop/` - Auto-import workflow
- `file/` - File I/O operations
- `email/` - Email sending
- `task/` - Task management

### Frontend Architecture

**Feature-Based Organization:**

```
app/
├── core/              # Singletons, guards, app-wide services
├── shared/            # Reusable components, services, models
└── features/          # Self-contained feature modules
    ├── book/
    ├── dashboard/
    ├── settings/
    └── ...
```

**Angular Standalone Components:**
- No NgModules
- Direct component imports
- Standalone architecture (Angular 20+)

**State Management:**
- RxJS for reactive state
- Services as state containers
- WebSocket for real-time updates

**Routing:**
- Lazy loading for features
- Route guards for authentication and permissions
- Custom route reuse strategy

### Backend Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| **Entity** | `*Entity.java` | `BookEntity.java` |
| **DTO (Request)** | `*Request.java` or `Create*`, `Update*` | `BookRequest.java`, `CreateLibraryRequest.java` |
| **DTO (Response)** | `*Response.java` or plain name | `BookResponse.java`, `Book.java` |
| **Repository** | `*Repository.java` | `BookRepository.java` |
| **Service** | `*Service.java` | `BookService.java` |
| **Controller** | `*Controller.java` | `BookController.java` |
| **Mapper** | `*Mapper.java` | `BookMapper.java` |
| **Exception** | `*Exception.java` | `BookNotFoundException.java` |

### Frontend Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| **Component** | `*.component.ts` | `book-card.component.ts` |
| **Service** | `*.service.ts` | `book.service.ts` |
| **Model** | `*.model.ts` | `book.model.ts` |
| **Guard** | `*.guard.ts` | `auth.guard.ts` |
| **Pipe** | `*.pipe.ts` | `secure.pipe.ts` |

### Code Patterns

**Backend Patterns:**

1. **Lombok Usage:**
   ```java
   @Entity
   @Getter
   @Setter
   @Builder
   @AllArgsConstructor
   @NoArgsConstructor
   public class BookEntity {
       // fields
   }
   ```

2. **MapStruct Mapping:**
   ```java
   @Mapper(componentModel = "spring")
   public interface BookMapper {
       BookResponse toResponse(BookEntity entity);
       BookEntity toEntity(BookRequest request);
   }
   ```

3. **Repository Pattern:**
   ```java
   public interface BookRepository extends JpaRepository<BookEntity, Long> {
       @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId")
       List<BookEntity> findByLibraryId(@Param("libraryId") Long libraryId);
   }
   ```

4. **Service Pattern:**
   ```java
   @Service
   @RequiredArgsConstructor
   public class BookService {
       private final BookRepository bookRepository;
       private final BookMapper bookMapper;

       public BookResponse getBook(Long id) {
           BookEntity entity = bookRepository.findById(id)
               .orElseThrow(() -> new BookNotFoundException(id));
           return bookMapper.toResponse(entity);
       }
   }
   ```

5. **Custom Security Annotations:**
   ```java
   @CheckBookAccess(bookIdParam = "bookId")
   public BookResponse updateBook(Long bookId, BookRequest request) {
       // method implementation
   }
   ```

6. **Field Locking (Metadata):**
   - Each metadata field has a corresponding `*_locked` boolean field
   - Locked fields are not overwritten during metadata updates
   - Example: `title` has `title_locked`

7. **Soft Delete:**
   - Books have `deleted` flag and `deletedAt` timestamp
   - Not physically removed from database

**Frontend Patterns:**

1. **Standalone Components:**
   ```typescript
   @Component({
       selector: 'app-book-card',
       standalone: true,
       imports: [CommonModule, PrimeNGModules],
       templateUrl: './book-card.component.html',
       styleUrls: ['./book-card.component.scss']
   })
   export class BookCardComponent {
       // component logic
   }
   ```

2. **Service with RxJS:**
   ```typescript
   @Injectable({ providedIn: 'root' })
   export class BookService {
       private books$ = new BehaviorSubject<Book[]>([]);

       getBooks(): Observable<Book[]> {
           return this.books$.asObservable();
       }
   }
   ```

3. **Route Guards:**
   ```typescript
   export const authGuard: CanActivateFn = (route, state) => {
       const authService = inject(AuthService);
       if (authService.isAuthenticated()) {
           return true;
       }
       return false;
   };
   ```

---

## Database Architecture

### Schema Overview

BookLore uses **MariaDB** with **64 Flyway migrations** for schema versioning.

**Migration Location:** `booklore-api/src/main/resources/db/migration/`

### Core Entity Relationships

```
User ←→ Library ←→ Book ←→ BookMetadata
  ↓                   ↓          ↓
Shelf ←→ Book      Progress   Author/Category/Tag
  ↓
MagicShelf
```

### Key Tables

#### Core Entities

1. **users** - User accounts
   - Primary authentication and user management
   - Relations: 1:1 user_permissions, 1:M shelves, M:M libraries

2. **library** - Book collections
   - Logical grouping of books
   - Relations: 1:M library_path, 1:M books, M:M users

3. **library_path** - Physical storage paths
   - Maps libraries to filesystem directories
   - Relations: M:1 library

4. **book** - Individual book files
   - Core book entity with file information
   - Fields: file_name, file_sub_path, book_type, file_size_kb, initial_hash, current_hash, deleted, deleted_at
   - Relations: M:1 library, M:1 library_path, 1:1 book_metadata, M:M shelves

5. **book_metadata** - Book metadata
   - Title, authors, publisher, ratings, etc.
   - Field-level locking (each field has `*_locked` boolean)
   - External IDs: asin, goodreads_id, hardcover_id, google_id, comicvine_id
   - Ratings: amazon_rating, goodreads_rating, hardcover_rating, personal_rating
   - Relations: 1:1 book, M:M authors/categories/moods/tags, 1:M reviews

6. **author** - Book authors
   - Unique name constraint
   - Relations: M:M book_metadata

7. **category** - Book genres/categories
   - Unique name constraint
   - Relations: M:M book_metadata

8. **mood** - Book moods/atmospheres
   - Relations: M:M book_metadata

9. **tag** - Custom user tags
   - Relations: M:M book_metadata

10. **shelf** - User collections
    - User-created book collections
    - Relations: M:1 users, M:M books

11. **magic_shelf** - Smart shelves
    - Rule-based dynamic collections
    - Filter stored as JSON
    - Relations: M:1 users

#### User & Permissions

12. **user_permissions** - Granular permissions
    - Fields: permission_upload, permission_download, permission_edit_metadata, permission_manipulate_library, permission_admin, permission_delete_books
    - Relations: 1:1 users

13. **user_settings** - User preferences
    - Key-value settings storage
    - Relations: M:1 users

#### Reading Progress

14. **user_book_progress** - Reading progress
    - Tracks progress for PDF, EPUB, CBX, and Kobo
    - Progress percentages and positions
    - Read status tracking
    - Relations: M:1 users, M:1 books

15. **epub_viewer_preference** - EPUB reader settings
    - Theme, font, font size, flow, spacing
    - Relations: Composite key (user_id, book_id)

16. **new_pdf_viewer_preference** - PDF reader settings
    - Page spread, view mode
    - Relations: Composite key (user_id, book_id)

17. **cbx_viewer_preference** - Comic reader settings
    - Fit mode, scroll mode, view mode, spread
    - Relations: Composite key (user_id, book_id)

#### Device Integration

18. **kobo_user_settings** - Kobo configuration
    - Enable/disable Kobo sync per user
    - Relations: 1:1 users

19. **kobo_reading_state** - Kobo progress sync
    - Syncs reading state with Kobo devices
    - Relations: Composite key (user_id, book_id)

20. **kobo_library_snapshot** - Kobo sync snapshots
    - Tracks library state for sync
    - Relations: M:1 users

21. **koreader_user** - KOReader integration
    - Separate credentials for KOReader sync
    - Relations: 1:1 users

22. **user_ephemera_settings** - Ephemera integration
    - External service integration
    - Relations: 1:1 users

#### OPDS & Email

23. **opds_user_v2** - OPDS feed access
    - Separate credentials for OPDS clients
    - Relations: M:M libraries

24. **email_provider_v2** - Email providers
    - SMTP configuration for sending books
    - Relations: M:1 users

25. **email_recipient_v2** - Email recipients
    - Saved email addresses
    - Relations: M:1 users

#### Metadata Management

26. **metadata_fetch_job** - Metadata fetch tasks
    - Background metadata fetching
    - Relations: M:1 users, 1:M proposals

27. **metadata_fetch_proposal** - Fetch results
    - Proposed metadata from external sources
    - Metadata stored as JSON
    - Relations: M:1 job, M:1 book

28. **book_review** - Reviews
    - User and public reviews
    - Relations: M:1 book_metadata

29. **book_note** - Private notes
    - User's private reading notes
    - Relations: M:1 users, M:1 books

#### BookDrop

30. **bookdrop_file** - Import queue
    - Files detected in BookDrop folder
    - Metadata stored as JSON
    - Relations: M:1 users, M:1 library

#### System Tables

31. **app_settings** - Application settings
    - Global configuration key-value pairs

32. **jwt_secret** - JWT signing keys
    - Secure token signing

33. **refresh_token** - OAuth refresh tokens
    - Token refresh flow
    - Relations: M:1 users

34. **task_history** - Background tasks
    - Task execution history
    - Relations: M:1 users

35. **task_cron_configuration** - Scheduled tasks
    - Cron-based task scheduling

### Migration Strategy

**Important Guidelines:**

1. **Never modify existing migrations** - Create new migrations instead
2. **Use Flyway naming:** `V{version}__{description}.sql`
   - Example: `V65__Add_New_Feature.sql`
3. **Incremental changes** - One logical change per migration
4. **Data migrations** - Include data transformation when changing schema
5. **Test migrations** - Always test on a copy of production data
6. **Backward compatibility** - Consider rollback scenarios

**Creating a New Migration:**

```bash
# Create new migration file
cd booklore-api/src/main/resources/db/migration/
# Find the next version number (currently at V64)
# Create: V65__Your_Description.sql
```

---

## API Structure

### Base URL

- **Development:** `http://localhost:8080/api/v1`
- **Production:** `{BASE_URL}/api/v1`

### API Documentation

- **Swagger UI:** `{BASE_URL}/api/v1/swagger-ui.html`
- **OpenAPI JSON:** `{BASE_URL}/api/v1/api-docs`

### Main API Endpoints

#### Books (`/api/v1/books`)

```
GET    /books                      # List all books
GET    /books/{id}                 # Get book details
GET    /books/batch?ids=1,2,3      # Get multiple books
DELETE /books?ids=1,2,3            # Delete books
GET    /books/{id}/content         # Stream book file
GET    /books/{id}/download        # Download book
GET    /books/{id}/viewer-setting  # Get viewer preferences
PUT    /books/{id}/viewer-setting  # Update viewer preferences
POST   /books/shelves              # Add books to shelves
POST   /books/progress             # Update reading progress
PUT    /books/read-status          # Update read status
POST   /books/reset-progress       # Reset progress
GET    /books/{id}/recommendations # Get book recommendations
```

#### Libraries (`/api/v1/libraries`)

```
GET    /libraries           # List all libraries
POST   /libraries           # Create library
GET    /libraries/{id}      # Get library details
PUT    /libraries/{id}      # Update library
DELETE /libraries/{id}      # Delete library
POST   /libraries/{id}/scan # Trigger library scan
GET    /libraries/{id}/stats # Get library statistics
```

#### Metadata (`/api/v1/metadata`)

```
POST   /metadata/fetch              # Fetch metadata
PUT    /metadata/{bookId}           # Update metadata
POST   /metadata/{bookId}/lock      # Lock metadata fields
POST   /metadata/{bookId}/unlock    # Unlock metadata fields
POST   /metadata/batch              # Batch metadata operations
```

#### Authentication (`/api/v1/auth`)

```
POST   /auth/login          # Login
POST   /auth/logout         # Logout
POST   /auth/refresh        # Refresh token
POST   /auth/change-password # Change password
GET    /auth/oauth2/providers # List OIDC providers
```

#### Users (`/api/v1/users`)

```
GET    /users               # List users (admin)
POST   /users               # Create user (admin)
GET    /users/{id}          # Get user details
PUT    /users/{id}          # Update user
DELETE /users/{id}          # Delete user
PUT    /users/{id}/permissions # Update permissions
```

#### Shelves (`/api/v1/shelves`)

```
GET    /shelves             # List user's shelves
POST   /shelves             # Create shelf
GET    /shelves/{id}        # Get shelf details
PUT    /shelves/{id}        # Update shelf
DELETE /shelves/{id}        # Delete shelf
```

#### Magic Shelves (`/api/v1/magic-shelves`)

```
GET    /magic-shelves       # List magic shelves
POST   /magic-shelves       # Create magic shelf
GET    /magic-shelves/{id}  # Get magic shelf details
PUT    /magic-shelves/{id}  # Update magic shelf
DELETE /magic-shelves/{id}  # Delete magic shelf
GET    /magic-shelves/{id}/books # Get books matching rules
```

#### BookDrop (`/api/v1/bookdrop`)

```
GET    /bookdrop/files      # List detected files
POST   /bookdrop/files/{id}/fetch-metadata # Fetch metadata
POST   /bookdrop/files/{id}/import # Import file
DELETE /bookdrop/files/{id} # Remove from queue
```

#### Kobo (`/api/v1/kobo`)

```
GET    /kobo/v1/library     # Kobo library sync
GET    /kobo/v1/library/{id}/state # Reading state
PUT    /kobo/v1/library/{id}/state # Update state
```

#### OPDS (`/api/v1/opds`)

```
GET    /opds                # Root feed
GET    /opds/catalog        # Main catalog
GET    /opds/search?q=...   # Search books
```

### Authentication Methods

1. **JWT Bearer Token** (Primary)
   ```
   Authorization: Bearer {token}
   ```

2. **Basic Auth** (OPDS, KOReader)
   ```
   Authorization: Basic {base64(username:password)}
   ```

3. **OIDC/OAuth2** (External providers)
   - Callback: `/oauth2-callback`
   - Providers: Authentik, Pocket ID, custom OIDC

4. **Remote Auth** (Reverse proxy)
   - Header-based authentication
   - Configurable header name

### Authorization

**Permission Levels:**
- `ADMIN` - Full system access
- `UPLOAD` - Upload books
- `DOWNLOAD` - Download books
- `EDIT_METADATA` - Modify book metadata
- `MANIPULATE_LIBRARY` - Library management
- `DELETE_BOOKS` - Delete books

**Access Control:**
- AOP aspects for book and library access validation
- Custom annotations: `@CheckBookAccess`, `@CheckLibraryAccess`
- User-library associations for multi-library setups

### WebSocket API

**Connection:** `ws://{BASE_URL}/ws`

**Topics:**
- `/topic/logs` - System logs
- `/topic/books/add` - New book notifications
- `/topic/books/remove` - Removed book notifications
- `/topic/tasks/progress` - Task progress updates
- `/topic/bookdrop` - BookDrop file events
- `/topic/metadata-batch` - Metadata fetch progress

**Protocol:** STOMP over WebSocket

---

## Build & Development Workflows

### Backend Development

**Common Gradle Tasks:**

```bash
# Build project
./gradlew build

# Run application
./gradlew bootRun

# Run tests
./gradlew test

# Clean build
./gradlew clean build

# Check for dependency updates
./gradlew dependencyUpdates

# Generate JAR
./gradlew bootJar
```

**Application Configuration:**

- Default: `application.yaml`
- Development: `application-dev.yml` (create manually)
- Production: Environment variables or `application-prod.yml`

**Environment Variables:**

```bash
DATABASE_URL=jdbc:mariadb://localhost:3306/booklore
DATABASE_USERNAME=booklore
DATABASE_PASSWORD=your_password
APP_PATH_BOOK=/path/to/books
APP_PATH_CONFIG=/path/to/config
```

### Frontend Development

**Common NPM Scripts:**

```bash
# Install dependencies
npm install

# Development server
npm start
# or
ng serve --open --watch --configuration development

# Production build
npm run build
# or
ng build --configuration production

# Run tests
npm test

# Run linter
npm run lint

# Fix linting issues
npm run lint:fix
```

**Development Server:**
- URL: `http://localhost:4200`
- Auto-reload on file changes
- Proxies API requests to `http://localhost:8080`

**Build Output:**
- Directory: `dist/booklore/`
- Optimized for production
- Gzip compression
- Bundle size limits enforced

### Docker Development

**Development Mode:**

```bash
# Start services
docker compose -f dev.docker-compose.yml up -d

# View logs
docker compose -f dev.docker-compose.yml logs -f

# Stop services
docker compose -f dev.docker-compose.yml down

# Rebuild and restart
docker compose -f dev.docker-compose.yml up -d --build
```

**Production Mode:**

```bash
# Start services
docker compose up -d

# View logs
docker compose logs -f booklore

# Stop services
docker compose down
```

**Environment Configuration:**

Edit `.env` file for configuration:

```ini
APP_USER_ID=0
APP_GROUP_ID=0
TZ=Etc/UTC
BOOKLORE_PORT=6060
DATABASE_URL=jdbc:mariadb://mariadb:3306/booklore
DB_USER=booklore
DB_PASSWORD=ChangeMe_BookLoreApp_2025!
```

### Database Management

**Access MariaDB:**

```bash
# Via Docker
docker exec -it mariadb mariadb -u booklore -p

# Locally
mysql -u booklore -p booklore
```

**Backup Database:**

```bash
# Via Docker
docker exec mariadb mariadb-dump -u root -p booklore > backup.sql

# Restore
docker exec -i mariadb mariadb -u booklore -p booklore < backup.sql
```

**View Migrations:**

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

---

## Testing Guidelines

### Backend Testing

**Test Structure:**

```
booklore-api/src/test/java/com/adityachandel/booklore/
├── controller/     # Controller integration tests
├── service/        # Service unit tests
├── repository/     # Repository tests
└── util/           # Utility tests
```

**Test Frameworks:**
- JUnit 5 (Jupiter)
- Mockito for mocking
- AssertJ for assertions
- Spring Boot Test for integration tests

**Example Test:**

```java
@SpringBootTest
@AutoConfigureMockMvc
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookService bookService;

    @Test
    void testGetBook() throws Exception {
        // Given
        BookResponse book = new BookResponse();
        book.setId(1L);
        when(bookService.getBook(1L)).thenReturn(book);

        // When & Then
        mockMvc.perform(get("/api/v1/books/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }
}
```

**Run Tests:**

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests BookServiceTest

# With coverage
./gradlew test jacocoTestReport
```

### Frontend Testing

**Test Structure:**

```
booklore-ui/src/app/
├── features/*/**.spec.ts    # Component tests
├── shared/services/**.spec.ts # Service tests
└── core/security/**.spec.ts   # Security tests
```

**Test Frameworks:**
- Jasmine for test structure
- Karma for test runner
- Angular Testing Utilities

**Example Test:**

```typescript
describe('BookCardComponent', () => {
    let component: BookCardComponent;
    let fixture: ComponentFixture<BookCardComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BookCardComponent]
        }).compileComponents();

        fixture = TestBed.createComponent(BookCardComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display book title', () => {
        component.book = { id: 1, title: 'Test Book' };
        fixture.detectChanges();

        const title = fixture.nativeElement.querySelector('.book-title');
        expect(title.textContent).toContain('Test Book');
    });
});
```

**Run Tests:**

```bash
# Run all tests
npm test

# Run with coverage
npm run test:coverage

# Run in headless mode
npm run test:headless
```

### Testing Best Practices

1. **Write tests for:**
   - New features
   - Bug fixes
   - Complex business logic
   - Security-critical code

2. **Test coverage goals:**
   - Services: 80%+ coverage
   - Controllers: 70%+ coverage
   - Utilities: 90%+ coverage

3. **Integration tests:**
   - Test full request/response cycles
   - Use test database
   - Clean up test data

4. **Unit tests:**
   - Mock dependencies
   - Test edge cases
   - Test error handling

---

## Common Tasks

### Adding a New Feature

1. **Create Feature Branch:**
   ```bash
   git checkout -b feat/feature-name
   ```

2. **Backend Changes:**
   - Create/update entities in `/model/entity/`
   - Create/update DTOs in `/model/dto/`
   - Create/update mapper in `/mapper/`
   - Implement service in `/service/`
   - Create controller in `/controller/`
   - Add tests

3. **Database Changes:**
   - Create migration in `db/migration/`
   - Name: `V{next-version}__Feature_Description.sql`

4. **Frontend Changes:**
   - Create feature module in `/features/`
   - Add components, services, models
   - Update routing in `app.routes.ts`
   - Add tests

5. **Test Locally:**
   ```bash
   # Backend
   ./gradlew test

   # Frontend
   npm test

   # Integration
   docker compose -f dev.docker-compose.yml up -d
   ```

6. **Commit & Push:**
   ```bash
   git add .
   git commit -m "feat: add feature description"
   git push origin feat/feature-name
   ```

### Adding a Database Migration

1. **Find Next Version:**
   ```bash
   ls booklore-api/src/main/resources/db/migration/ | tail -1
   # Current: V64__*.sql
   # Next: V65
   ```

2. **Create Migration File:**
   ```bash
   cd booklore-api/src/main/resources/db/migration/
   touch V65__Add_My_Feature.sql
   ```

3. **Write Migration:**
   ```sql
   -- Add new table
   CREATE TABLE my_new_table (
       id BIGINT AUTO_INCREMENT PRIMARY KEY,
       name VARCHAR(255) NOT NULL,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

   -- Add index
   CREATE INDEX idx_name ON my_new_table(name);
   ```

4. **Test Migration:**
   - Run application
   - Flyway will automatically apply migration
   - Verify in database

5. **Rollback Plan:**
   - Create manual rollback script if needed
   - Test rollback procedure

### Updating Dependencies

**Backend:**

```bash
# Check for updates
./gradlew dependencyUpdates

# Update Gradle wrapper
./gradlew wrapper --gradle-version=8.x

# Update Spring Boot version in build.gradle
# Test thoroughly after updates
./gradlew clean build test
```

**Frontend:**

```bash
# Check for updates
npm outdated

# Update Angular
ng update @angular/core @angular/cli

# Update other dependencies
npm update

# Update major versions carefully
npm install package@latest

# Test after updates
npm test
npm run build
```

### Debugging

**Backend Debugging:**

1. **Run with Debug Mode:**
   ```bash
   ./gradlew bootRun --debug-jvm
   ```

2. **Attach Debugger:**
   - IntelliJ IDEA: Run → Attach to Process
   - Port: 5005

3. **Logging:**
   ```yaml
   # application-dev.yml
   logging:
     level:
       com.adityachandel.booklore: DEBUG
       org.springframework.web: DEBUG
   ```

**Frontend Debugging:**

1. **Browser DevTools:**
   - Chrome DevTools
   - Angular DevTools extension

2. **Enable Source Maps:**
   ```json
   // angular.json
   "sourceMap": true
   ```

3. **Logging:**
   ```typescript
   console.log('Debug info:', data);
   ```

### Code Review Checklist

- [ ] Code follows project conventions
- [ ] Tests added/updated
- [ ] Database migrations included (if needed)
- [ ] API documentation updated
- [ ] No security vulnerabilities
- [ ] Error handling implemented
- [ ] Logging added for important operations
- [ ] Performance considerations addressed
- [ ] Backward compatibility maintained
- [ ] Documentation updated

---

## Key Conventions

### Git Workflow

**Branching Strategy:**

- `main` - Production-ready code
- `develop` - Development integration branch
- `feat/*` - New features
- `fix/*` - Bug fixes
- `refactor/*` - Code refactoring
- `docs/*` - Documentation updates
- `test/*` - Test additions/updates

**Commit Message Format:**

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <description>

[optional body]

[optional footer]
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation
- `style` - Code style (formatting, etc.)
- `refactor` - Code refactoring
- `test` - Tests
- `chore` - Maintenance tasks

**Examples:**
```
feat: add Kobo sync support
fix: correct metadata locking behavior
docs: update API documentation
refactor: simplify book service logic
test: add tests for library scanning
```

### Code Style

**Backend (Java):**

- Follow standard Java conventions
- Use Lombok to reduce boilerplate
- Prefer immutability where possible
- Use meaningful variable names
- Keep methods focused and small
- Add JavaDoc for public APIs

**Frontend (TypeScript):**

- Follow [Angular Style Guide](https://angular.io/guide/styleguide)
- Use TypeScript strict mode
- Prefer const over let
- Use async/await over raw promises
- Keep components focused
- Extract reusable logic to services

**Formatting:**

- Backend: Default IntelliJ IDEA formatting
- Frontend: Prettier with ESLint
- Consistent indentation (2 spaces for TS/HTML, 4 for Java)

### Security Best Practices

1. **Input Validation:**
   - Validate all user inputs
   - Use Bean Validation annotations
   - Sanitize file paths

2. **Authentication:**
   - Use JWT for stateless auth
   - Implement token refresh
   - Support OIDC for SSO

3. **Authorization:**
   - Check permissions at service layer
   - Use custom annotations for access control
   - Validate user has access to resources

4. **Data Protection:**
   - Hash passwords (BCrypt)
   - Encrypt sensitive configuration
   - Use HTTPS in production

5. **File Operations:**
   - Validate file types
   - Limit file sizes
   - Prevent path traversal

6. **SQL Injection:**
   - Use JPA/JPQL instead of raw SQL
   - Parameterize queries
   - Validate inputs

### Performance Considerations

1. **Database:**
   - Add indexes for frequently queried fields
   - Use pagination for large result sets
   - Optimize N+1 queries with JPA fetch strategies
   - Use database connection pooling

2. **API:**
   - Implement caching where appropriate
   - Use gzip compression
   - Minimize payload sizes
   - Support conditional requests (ETags)

3. **Frontend:**
   - Lazy load features
   - Use virtual scrolling for long lists
   - Optimize images and assets
   - Implement infinite scrolling

4. **File Processing:**
   - Process large files in chunks
   - Use async processing for heavy tasks
   - Implement progress reporting

---

## Important Guidelines for AI Assistants

### Understanding the Codebase

1. **Read Before Modifying:**
   - Always read existing code before making changes
   - Understand the current implementation patterns
   - Follow established conventions

2. **File Locations:**
   - Backend entities: `booklore-api/src/main/java/com/adityachandel/booklore/model/entity/`
   - Backend services: `booklore-api/src/main/java/com/adityachandel/booklore/service/`
   - Frontend components: `booklore-ui/src/app/features/`
   - Migrations: `booklore-api/src/main/resources/db/migration/`

3. **Dependencies:**
   - Check existing dependencies before adding new ones
   - Consider bundle size impact for frontend
   - Verify license compatibility

### Making Changes

1. **Database Changes:**
   - NEVER modify existing migrations
   - Create new migration for schema changes
   - Include data migration if needed
   - Test on sample data

2. **API Changes:**
   - Maintain backward compatibility
   - Version APIs if breaking changes needed
   - Update OpenAPI documentation
   - Test with existing clients

3. **Code Quality:**
   - Write tests for new code
   - Update existing tests if behavior changes
   - Follow existing patterns
   - Add logging for important operations

4. **Security:**
   - Validate all inputs
   - Check authorization
   - Avoid hardcoding secrets
   - Use parameterized queries

### Common Pitfalls to Avoid

1. **Don't:**
   - Modify existing Flyway migrations
   - Break backward compatibility without versioning
   - Skip input validation
   - Hardcode file paths or credentials
   - Create N+1 query problems
   - Expose sensitive data in APIs
   - Add unnecessary dependencies

2. **Do:**
   - Create new migrations for schema changes
   - Add tests for new features
   - Follow established patterns
   - Use environment variables for configuration
   - Optimize database queries
   - Validate user access to resources
   - Keep dependencies up to date

### Working with Specific Components

**Metadata System:**
- Respect field locking mechanism
- Each metadata field has a `*_locked` boolean
- Locked fields should not be overwritten
- Example: If `title_locked` is true, don't update `title`

**File Management:**
- Always validate file paths
- Use utility methods from `FileService`
- Handle file not found scenarios
- Clean up temporary files

**User Permissions:**
- Check permissions at service layer
- Use `@PreAuthorize` or custom annotations
- Validate library and book access
- Consider admin bypass where appropriate

**WebSocket:**
- Send progress updates for long-running tasks
- Use appropriate topics
- Handle connection failures gracefully

**Device Integration:**
- Kobo and KOReader have separate auth mechanisms
- Respect device-specific formats (KEPUB for Kobo)
- Handle sync conflicts appropriately

### Testing Requirements

1. **Unit Tests:**
   - Test new service methods
   - Mock dependencies
   - Test edge cases and error conditions

2. **Integration Tests:**
   - Test controller endpoints
   - Verify database operations
   - Test authentication/authorization

3. **Manual Testing:**
   - Test UI changes in browser
   - Verify API with Swagger UI
   - Test with Docker deployment

### Documentation

1. **Code Documentation:**
   - Add JavaDoc/TSDoc for public methods
   - Explain complex logic with comments
   - Document assumptions and limitations

2. **API Documentation:**
   - OpenAPI annotations for new endpoints
   - Update Swagger descriptions
   - Document request/response examples

3. **User Documentation:**
   - Update README if user-facing changes
   - Add setup instructions for new features
   - Document configuration options

### Useful Commands Reference

**Backend:**
```bash
# Build
./gradlew clean build

# Run
./gradlew bootRun

# Test
./gradlew test

# Check dependencies
./gradlew dependencyUpdates
```

**Frontend:**
```bash
# Install
npm install

# Develop
npm start

# Build
npm run build

# Test
npm test

# Lint
npm run lint
```

**Docker:**
```bash
# Dev mode
docker compose -f dev.docker-compose.yml up -d

# Production
docker compose up -d

# Logs
docker compose logs -f booklore

# Rebuild
docker compose up -d --build
```

**Database:**
```bash
# Connect
docker exec -it mariadb mariadb -u booklore -p

# Backup
docker exec mariadb mariadb-dump -u root -p booklore > backup.sql

# Restore
docker exec -i mariadb mariadb -u booklore -p booklore < backup.sql
```

### Quick Reference: File Paths

| Purpose | Path |
|---------|------|
| Backend source | `booklore-api/src/main/java/com/adityachandel/booklore/` |
| Frontend source | `booklore-ui/src/app/` |
| Database migrations | `booklore-api/src/main/resources/db/migration/` |
| Backend config | `booklore-api/src/main/resources/application.yaml` |
| Frontend config | `booklore-ui/src/environments/environment.ts` |
| Docker compose | `docker-compose.yml` (prod), `dev.docker-compose.yml` (dev) |
| Build files | `booklore-api/build.gradle`, `booklore-ui/package.json` |
| Documentation | `docs/`, `README.md`, `CONTRIBUTING.md` |

---

## Conclusion

BookLore is a feature-rich, well-architected application with clear separation of concerns and modern development practices. When working on this codebase:

- **Understand the architecture** before making changes
- **Follow established conventions** for consistency
- **Write tests** to maintain quality
- **Document your changes** for future maintainers
- **Consider performance and security** in all implementations
- **Respect backward compatibility** when modifying APIs or schemas

For questions or clarifications, refer to:
- [README.md](README.md) - Project overview and setup
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- API Documentation at `/api/v1/swagger-ui.html`
- Discord community: https://discord.gg/Ee5hd458Uz

Happy coding!
