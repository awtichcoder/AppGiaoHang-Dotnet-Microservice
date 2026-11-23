using System.IdentityModel.Tokens.Jwt;
using IdentityService.Contracts;
using IdentityService.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace IdentityService.Controllers;

[ApiController]
[Route("api/identity")]
public sealed class AuthController(IIdentityManager identityManager) : ControllerBase
{
    [AllowAnonymous]
    [HttpPost("register")]
    public async Task<ActionResult<UserResponse>> Register(RegisterRequest request, CancellationToken cancellationToken)
    {
        var user = await identityManager.RegisterAsync(request, cancellationToken);
        return StatusCode(StatusCodes.Status201Created, user);
    }

    [AllowAnonymous]
    [HttpPost("login")]
    public async Task<ActionResult<TokenResponse>> Login(LoginRequest request, CancellationToken cancellationToken)
        => Ok(await identityManager.LoginAsync(request, cancellationToken));

    [AllowAnonymous]
    [HttpPost("refresh")]
    public async Task<ActionResult<TokenResponse>> Refresh(RefreshRequest request, CancellationToken cancellationToken)
        => Ok(await identityManager.RefreshAsync(request, cancellationToken));

    [AllowAnonymous]
    [HttpPost("logout")]
    public async Task<ActionResult<LogoutResponse>> Logout(LogoutRequest request, CancellationToken cancellationToken)
        => Ok(await identityManager.LogoutAsync(request, cancellationToken));

    [Authorize]
    [HttpGet("me")]
    public async Task<ActionResult<UserResponse>> GetMe(CancellationToken cancellationToken)
    {
        var value = User.FindFirst(JwtRegisteredClaimNames.Sub)?.Value;
        if (!Guid.TryParse(value, out var userId))
            return Unauthorized();
        return Ok(await identityManager.GetMeAsync(userId, cancellationToken));
    }

    [Authorize(Roles = "ADMIN")]
    [HttpPost("admin/users")]
    public async Task<ActionResult<UserResponse>> CreatePrivilegedUser(
        CreateAdminUserRequest request,
        CancellationToken cancellationToken)
    {
        var user = await identityManager.CreatePrivilegedUserAsync(request, cancellationToken);
        return StatusCode(StatusCodes.Status201Created, user);
    }
}
