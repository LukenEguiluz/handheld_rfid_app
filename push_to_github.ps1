# Script para hacer push al repositorio de GitHub
cd $PSScriptRoot

Write-Host "=== Estado del repositorio Git ===" -ForegroundColor Cyan
git status

Write-Host "`n=== Remote configurado ===" -ForegroundColor Cyan
git remote -v

Write-Host "`n=== Rama actual ===" -ForegroundColor Cyan
git branch

Write-Host "`n=== Últimos commits ===" -ForegroundColor Cyan
git log --oneline -3

Write-Host "`n=== Intentando push a GitHub ===" -ForegroundColor Yellow
git push -u origin main

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✓ Push completado exitosamente!" -ForegroundColor Green
} else {
    Write-Host "`n✗ Error en el push. Verifica las credenciales y la conexión." -ForegroundColor Red
}


