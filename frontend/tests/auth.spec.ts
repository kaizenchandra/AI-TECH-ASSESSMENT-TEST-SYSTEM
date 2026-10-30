import {test,expect} from '@playwright/test';
test('unauthenticated visitor gets accessible sign-in and validation',async({page})=>{await page.goto('/');await expect(page.getByRole('heading',{name:'Sign in to your workspace'})).toBeVisible();await page.getByRole('button',{name:'Sign in →'}).click();await expect(page.getByRole('alert').first()).toBeVisible();});
test('candidate API rejects anonymous attempt access',async({request})=>{const result=await request.get('/api/v1/candidate/assignments');expect(result.status()).toBe(401);});
