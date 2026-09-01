using IdentityService.Contracts;
using IdentityService.Entities;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using Microsoft.IdentityModel.Tokens;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using AspNetCore.Identity.Mongo.Model;

namespace IdentityService.Controllers
{
    
    [ApiController]
    [Route("api/identity")]
    public class AuthController : ControllerBase
    {
        private readonly UserManager<ApplicationUser> _userManager;
        private readonly RoleManager<MongoRole> _roleManager;
        private readonly IConfiguration _configuration;

        public AuthController(
            UserManager<ApplicationUser> userManager,
            RoleManager<MongoRole> roleManager,
            IConfiguration configuration)
        {
            _userManager = userManager;
            _roleManager = roleManager;
            _configuration = configuration;
        }


        // POST: api/identity/register
        [HttpPost("register")]
        public async Task<IActionResult> Register([FromBody] RegisterRequest request)
        {
            // validation request email and password
            if (string.IsNullOrWhiteSpace(request.Email) || string.IsNullOrWhiteSpace(request.Password))
                return BadRequest(new { message = "Dữ liệu không hợp lệ!" });

            //check if user already exists
            var userExists = await _userManager.FindByEmailAsync(request.Email);
            if (userExists != null)
                return StatusCode(StatusCodes.Status409Conflict, new { message = "Email đã tồn tại!" });

            var user = new ApplicationUser
            {
                Email = request.Email,
                UserName = request.Email,
                FullName = request.FullName,
                PhoneNumber = request.Phone
            };

            var result = await _userManager.CreateAsync(user, request.Password);
            if (!result.Succeeded)
                return BadRequest(result.Errors);

            // kiểm tra và tạo role nếu chưa tồn tại, sau đó gán role cho user
            var roleName = string.IsNullOrWhiteSpace(request.Role) ? "CUSTOMER" : request.Role.ToUpper();
            if (!await _roleManager.RoleExistsAsync(roleName))
            {
                await _roleManager.CreateAsync(new MongoRole(roleName));
            }

            await _userManager.AddToRoleAsync(user, roleName);

            return StatusCode(StatusCodes.Status201Created, new { message = "Đăng ký thành công!" });
        }

        // POST: api/identity/login
        [HttpPost("login")]
        public async Task<IActionResult> Login([FromBody] LoginRequest request)
        {
            // validation request email and password
            if (string.IsNullOrWhiteSpace(request.Email) || string.IsNullOrWhiteSpace(request.Password))
                return BadRequest(new { message = "Thiếu hoặc sai tham số!" });

            var user = await _userManager.FindByEmailAsync(request.Email);
            if (user == null || !await _userManager.CheckPasswordAsync(user, request.Password))
                return Unauthorized(new { message = "Email hoặc mật khẩu không đúng!" });

            var userRoles = await _userManager.GetRolesAsync(user);

            var authClaims = new List<Claim>
            {
                new Claim(JwtRegisteredClaimNames.Sub, user.Id.ToString()),
                new Claim(JwtRegisteredClaimNames.Email, user.Email!),
                new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString())
            };

            foreach (var role in userRoles)
            {
                authClaims.Add(new Claim(ClaimTypes.Role, role));
            }

            var token = GetToken(authClaims);

            return Ok(new
            {
                token = new JwtSecurityTokenHandler().WriteToken(token),
                expiration = token.ValidTo
            });
        }

        // GET: api/identity/me
        [Authorize]
        [HttpGet("me")]
        public async Task<IActionResult> GetMe()
        {
            var userId = User.FindFirstValue(ClaimTypes.NameIdentifier) ?? User.FindFirstValue(JwtRegisteredClaimNames.Sub);
            
            if (string.IsNullOrEmpty(userId))
                return Unauthorized();

            var user = await _userManager.FindByIdAsync(userId);
            if (user == null)
                return Unauthorized();

            var roles = await _userManager.GetRolesAsync(user);

            return Ok(new
            {
                userId = user.Id.ToString(),
                email = user.Email,
                fullName = user.FullName,
                phone = user.PhoneNumber, 
                role = roles.FirstOrDefault()
            });
        }


        // tạo token JWT và set limit time expiration cho token 60 minutes
        private JwtSecurityToken GetToken(List<Claim> authClaims)
        {
            var authSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_configuration["JWT_SECRET"] ?? "SuperSecretKey1234567890_PleaseChangeMe"));

            var token = new JwtSecurityToken(
                issuer: _configuration["JWT_ISSUER"] ?? "DeliveryApp",
                audience: _configuration["JWT_AUDIENCE"] ?? "DeliveryAppClient",
                expires: DateTime.Now.AddMinutes(60), // limit token expiration to 60 minutes
                claims: authClaims,
                signingCredentials: new SigningCredentials(authSigningKey, SecurityAlgorithms.HmacSha256)
            );

            return token;
        }


        
    }
}
